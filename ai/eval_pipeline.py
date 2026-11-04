"""Evaluate the deployed vision flow including UNKNOWN, without changing weights."""
from __future__ import annotations
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
import numpy as np
import torch
from train_classify import CLASSES, list_samples
from vision.pipeline import VisionPipeline, load_config
from vision.preprocess import read_rgb


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--detector", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    report = {"selection_basis": "Not used for model selection; diagnostic end-to-end evaluation",
              "detector_sha256": hashlib.sha256(args.detector.read_bytes()).hexdigest(), "models": {},
              "limitations": ["Energy threshold calibrated on in-distribution validation only; not an OOD detector validation",
                              "No independent patient/source validation", "No clinical safety evaluation"]}
    for name, checkpoint in [("baseline", args.baseline), ("candidate", args.candidate)]:
        pipeline = VisionPipeline(load_config())
        pipeline.load(args.detector, checkpoint)
        energies = []
        for path, _ in list_samples(args.data, "valid"):
            image = read_rgb(path)
            if image is not None:
                energies.append(pipeline.classifier.probabilities(image)[1])
        pipeline.energy_max = float(np.percentile(energies, 95))
        counts, failures, rows = Counter(), Counter(), []
        per_class = {label: Counter() for label in CLASSES}
        for path, target in list_samples(args.data, "test"):
            result = pipeline.predict_path(path)
            classification = result.get("classification") or {}
            predicted = classification.get("label", "UNKNOWN")
            truth = CLASSES[target]
            accepted = predicted != "UNKNOWN"
            correct = predicted == truth
            counts["total"] += 1
            counts["accepted"] += int(accepted)
            counts["correct"] += int(correct)
            per_class[truth]["total"] += 1
            per_class[truth]["accepted"] += int(accepted)
            per_class[truth]["correct"] += int(correct)
            reasons = (result.get("quality") or {}).get("reasons", []) + classification.get("gate_fail", [])
            if not accepted:
                failures.update(reasons)
            rows.append({"file": path.name, "truth": truth, "predicted": predicted, "failures": reasons})
        report["models"][name] = {
            "energy_max": pipeline.energy_max, "counts": dict(counts),
            "coverage": counts["accepted"] / counts["total"],
            "unknown_rate": 1 - counts["accepted"] / counts["total"],
            "correct_result_rate_all_inputs": counts["correct"] / counts["total"],
            "accepted_accuracy": counts["correct"] / counts["accepted"] if counts["accepted"] else None,
            "per_class": {key: dict(value) for key, value in per_class.items()},
            "unknown_failure_counts_nonexclusive": dict(failures), "predictions": rows}
        print(name, {key: value for key, value in report["models"][name].items()
                     if key not in {"predictions", "per_class"}}, flush=True)
        del pipeline
        if torch.cuda.is_available(): torch.cuda.empty_cache()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2), encoding="utf-8")

if __name__ == "__main__": main()
