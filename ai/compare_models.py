"""Compare a validation-selected classifier with a baseline on a fixed test split.

Does not replace deployed weights or claim end-to-end/clinical performance.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import time
from pathlib import Path
import numpy as np
import torch
from torch.utils.data import DataLoader
from train_classify import CLASSES, WoundDataset, build_transforms, list_samples
from eval_classify import load_model, confusion, metrics_from_counts


def synchronize(device):
    if device.type == "cuda":
        torch.cuda.synchronize()


@torch.inference_mode()
def collect(model, rows, device, batch):
    loader = DataLoader(WoundDataset(rows, build_transforms(False)), batch_size=batch, shuffle=False)
    logits, targets, timings = [], [], []
    # Warm up separately so load/initial CUDA compilation does not skew latency.
    model(torch.zeros(1, 3, 224, 224, device=device)); synchronize(device)
    for images, labels in loader:
        images = images.to(device); synchronize(device)
        start = time.perf_counter()
        result = model(images); synchronize(device)
        timings.append((time.perf_counter() - start) * 1000 / len(images))
        logits.append(result.cpu()); targets.append(labels)
    logits = torch.cat(logits); targets = torch.cat(targets)
    guesses = logits.argmax(1)
    report = metrics_from_counts(confusion(guesses, targets, len(CLASSES)))
    report["accuracy"] = float((guesses == targets).float().mean())
    report["batched_forward_ms_per_image_median"] = float(np.median(timings))
    report["batched_forward_ms_per_image_p95"] = float(np.percentile(timings, 95))
    probabilities = logits.softmax(1)
    top2 = probabilities.topk(2, dim=1).values
    accepted = (top2[:, 0] >= 0.65) & ((top2[:, 0] - top2[:, 1]) >= 0.15)
    report["confidence_filter_only"] = {
        "coverage": float(accepted.float().mean()),
        "accepted_accuracy": float((guesses[accepted] == targets[accepted]).float().mean()) if accepted.any() else None,
        "note": "Classifier confidence/margin only; excludes detector, quality and energy gates"
    }
    return report, guesses.numpy(), targets.numpy()


def f1(pred, labels):
    return metrics_from_counts(confusion(torch.as_tensor(pred), torch.as_tensor(labels), len(CLASSES)))["macro_f1"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--batch", type=int, default=8)
    args = parser.parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    reports, predictions = {}, {}
    for name, checkpoint in [("baseline", args.baseline), ("candidate", args.candidate)]:
        model = load_model(checkpoint, device)
        valid, _, _ = collect(model, list_samples(args.data, "valid"), device, args.batch)
        test, pred, labels = collect(model, list_samples(args.data, "test"), device, args.batch)
        reports[name] = {"checkpoint": str(checkpoint), "sha256": hashlib.sha256(checkpoint.read_bytes()).hexdigest(),
                         "validation": valid, "test": test}
        predictions[name] = pred
        del model
        if device.type == "cuda": torch.cuda.empty_cache()
    # Selection rule uses validation only, not test scores.
    choose_candidate = reports["candidate"]["validation"]["macro_f1"] > reports["baseline"]["validation"]["macro_f1"]
    rng = np.random.default_rng(42)
    differences = []
    for _ in range(2000):
        # Stratified paired bootstrap preserves rare-class representation.
        indices = np.concatenate([rng.choice(np.where(labels == label)[0], size=int((labels == label).sum()), replace=True)
                                  for label in range(len(CLASSES))])
        differences.append(f1(predictions["candidate"][indices], labels[indices]) -
                           f1(predictions["baseline"][indices], labels[indices]))
    result = {"stage": "classifier_only", "device": str(device), "torch": str(torch.__version__),
              "n_test": len(labels), "selection": "candidate" if choose_candidate else "baseline",
              "selection_basis": "validation macro F1 only", "models": reports,
              "test_macro_f1_delta": reports["candidate"]["test"]["macro_f1"] - reports["baseline"]["test"]["macro_f1"],
              "paired_stratified_bootstrap_delta_95_ci": np.percentile(differences, [2.5, 97.5]).tolist(),
              "limitations": ["Same existing dataset, not an external independent clinical evaluation",
                              "Patient/source independence and augmented near-duplicates are unverified",
                              "Confidence-filter metrics exclude quality/detector/energy gates",
                              "Timing is batched forward pass only, excludes image IO, preprocessing, APIs and queueing"],
              "predictions": {"truth": labels.tolist(), **{k: v.tolist() for k, v in predictions.items()}}}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({"selection": result["selection"], "delta": result["test_macro_f1_delta"],
                      "delta_ci": result["paired_stratified_bootstrap_delta_95_ci"]}), flush=True)

if __name__ == "__main__": main()
