"""한 장의 사진을 품질 → 탐지 → 분류 → 게이트 순서로 처리한다."""

from __future__ import annotations

import argparse
import json
import logging
import os
import time
import uuid
from pathlib import Path

import numpy as np
import cv2
import torch
import yaml

from train_classify import list_samples
from vision.classify import WoundClassifier
from vision.detect import WoundDetector
from vision.gate import GateConfig, apply_gate
from vision.preprocess import read_rgb
from vision.quality import QualityConfig, check_image

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "config.yaml"
DEFAULT_CLASSIFY = ROOT / "runs" / "classify_locked" / "best.pt"
DEFAULT_DETECT = ROOT / "runs" / "det_v8s_wound" / "weights" / "best.pt"
DEFAULT_ENERGY = ROOT / "models" / "ood-energy.json"
PERFORMANCE = logging.getLogger("skincare.performance")


def load_config(path: Path = DEFAULT_CONFIG) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


class VisionPipeline:
    def __init__(self, config: dict | None = None, energy_max: float | None = None):
        self.config = config or load_config()
        q = self.config["quality"]
        g = self.config["gate"]
        self.quality_cfg = QualityConfig(**q)
        self.gate_cfg = GateConfig(
            top1=g["top1"], margin=g["margin"], det_conf=g["det_conf"], min_area=g["min_area"]
        )
        self.min_crop_px = g["min_crop_px"]
        self.energy_max = energy_max
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.detector: WoundDetector | None = None
        self.classifier: WoundClassifier | None = None
        self.cpu_threads = max(1, min(4, int(os.environ.get('AI_CPU_THREADS', '2'))))

    def configure_cpu(self):
        # Keep OpenCV's image operations on the dedicated vision worker, avoiding
        # a second native pool after quality-stage stalls in the container.
        # Torch retains its independently bounded inference thread count.
        if cv2.getNumThreads() != 1:
            cv2.setNumThreads(1)
        if self.device.type == 'cpu':
            if torch.get_num_threads() != self.cpu_threads:
                torch.set_num_threads(self.cpu_threads)

    def load(self, detect_path: Path = DEFAULT_DETECT, classify_path: Path = DEFAULT_CLASSIFY) -> None:
        def stage(value):
            PERFORMANCE.info(json.dumps({'event': 'vision_prepare_stage', 'stage': value,
                                         'device': self.device.type, 'cpu_threads': self.cpu_threads}))
        stage('cpu-thread-setup')
        self.configure_cpu()
        det = self.config["detect"]
        device = "0" if self.device.type == "cuda" else "cpu"
        stage('detector-load')
        self.detector = WoundDetector(detect_path, det["conf"], det["iou"], det["max_det"], device)
        stage('classifier-load')
        self.classifier = WoundClassifier(classify_path, self.device)
        stage('models-loaded')

    @property
    def ready(self) -> bool:
        return self.detector is not None and self.classifier is not None

    def warmup(self) -> dict:
        """Prepare both models without creating a patient analysis or bypassing a gate."""
        if not self.ready:
            raise RuntimeError("Models must be loaded before warmup")
        started = time.perf_counter()
        # Exercise camera-sized preprocessing, both models, and common aspect
        # ratios. Uniform small images did not prepare the real upload path.
        rng = np.random.default_rng(20261107)
        detector_ms = classifier_ms = 0.0
        for height, width in ((640, 640), (1200, 1600), (1600, 1200)):
            image = rng.integers(40, 210, (height, width, 3), dtype=np.uint8)
            self.configure_cpu()
            quality = check_image(image, self.quality_cfg)
            if not quality['ok']:
                raise RuntimeError('Synthetic warmup image failed quality check')
            tick = time.perf_counter()
            self.detector.predict(image)
            detector_ms += (time.perf_counter() - tick) * 1000
            # Ultralytics may change the Torch thread count during first setup.
            self.configure_cpu()
            tick = time.perf_counter()
            probabilities, energy = self.classifier.probabilities(image)
            classifier_ms += (time.perf_counter() - tick) * 1000
            if len(probabilities) != 5 or not np.isfinite(probabilities).all() or not np.isfinite(energy):
                raise RuntimeError("Invalid classifier warmup output")
        result = {"event": "vision_warmup", "device": self.device.type,
                  "detector_ms": round(detector_ms, 2),
                  "classifier_ms": round(classifier_ms, 2),
                  "total_ms": round((time.perf_counter() - started) * 1000, 2),
                  "cpu_threads": self.cpu_threads,
                  "synthetic_input_only": True}
        PERFORMANCE.info(json.dumps(result))
        return result

    def predict_path(self, path: Path) -> dict:
        image = read_rgb(path)
        if image is None:
            return self._empty("decode_fail")
        return self.predict_array(image)

    def predict_array(self, image: np.ndarray) -> dict:
        self.configure_cpu()
        started = time.perf_counter()
        timings = {}
        try:
            return self._predict_array(image, timings)
        finally:
            PERFORMANCE.info(json.dumps({"event": "vision_timing", "device": self.device.type,
                "total_ms": round((time.perf_counter() - started) * 1000, 2), **timings}))

    def _predict_array(self, image: np.ndarray, timings: dict) -> dict:
        started = time.perf_counter()
        quality = check_image(image, self.quality_cfg)
        timings["quality_ms"] = round((time.perf_counter() - started) * 1000, 2)
        result = {
            "request_id": str(uuid.uuid4()),
            "quality": quality,
            "detection": None,
            "extra_boxes": [],
            "classification": None,
            "depth_hint": None,
            "area_ratio": None,
            "model_versions": {"detect": "yolov8s-wound", "classify": "vit-b16-locked"},
        }
        if not quality["ok"]:
            return result
        if not self.ready:
            raise RuntimeError("모델이 로드되지 않았습니다.")

        started = time.perf_counter()
        boxes = self.detector.predict(image)
        timings["detector_ms"] = round((time.perf_counter() - started) * 1000, 2)
        if not boxes:
            result["classification"] = apply_gate(None, None, None, None, self.energy_max, self.gate_cfg)
            return result

        (box, score), *rest = boxes
        _x, _y, w, h = box
        area = w * h
        result["detection"] = {"box": [round(v, 4) for v in box], "score": round(score, 4)}
        result["extra_boxes"] = [[round(v, 4) for v in extra] for extra, _ in rest]
        result["area_ratio"] = round(area, 4)
        short_side = min(w * quality["width"], h * quality["height"])
        if short_side < self.min_crop_px or score < self.gate_cfg.det_conf or area < self.gate_cfg.min_area:
            result["classification"] = apply_gate(None, score, area, None, self.energy_max, self.gate_cfg)
            return result

        started = time.perf_counter()
        self.configure_cpu()
        probs, energy = self.classifier.probabilities(image)
        timings["classifier_ms"] = round((time.perf_counter() - started) * 1000, 2)
        result["classification"] = apply_gate(probs, score, area, energy, self.energy_max, self.gate_cfg)
        result["classification"]["energy"] = round(energy, 4)
        return result

    def _empty(self, reason: str) -> dict:
        return {
            "request_id": str(uuid.uuid4()),
            "quality": {"ok": False, "reasons": [reason], "width": 0, "height": 0, "mean_luma": None, "lap_var": None},
            "detection": None,
            "extra_boxes": [],
            "classification": None,
            "depth_hint": None,
            "area_ratio": None,
            "model_versions": {"detect": "yolov8s-wound", "classify": "vit-b16-locked"},
        }


def fit_energy(pipeline: VisionPipeline, data_root: Path, split: str = "valid") -> float:
    energies = []
    for path, _label in list_samples(data_root, split):
        image = read_rgb(path)
        if image is None or pipeline.classifier is None:
            continue
        _probs, energy = pipeline.classifier.probabilities(image)
        energies.append(energy)
    if not energies:
        raise SystemExit("valid 이미지가 없습니다.")
    return float(np.percentile(energies, 95))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", type=Path)
    parser.add_argument("--fit-energy", action="store_true")
    parser.add_argument("--data", type=Path, default=ROOT.parent / "datasets" / "classify_locked")
    args = parser.parse_args()

    pipeline = VisionPipeline()
    pipeline.load()
    if args.fit_energy:
        energy_max = fit_energy(pipeline, args.data)
        DEFAULT_ENERGY.parent.mkdir(parents=True, exist_ok=True)
        DEFAULT_ENERGY.write_text(
            json.dumps({"energy_max": energy_max, "run_id": "classify_locked"}, indent=2),
            encoding="utf-8",
        )
        print(json.dumps({"energy_max": energy_max}, ensure_ascii=False))
        pipeline.energy_max = energy_max
    elif DEFAULT_ENERGY.exists():
        pipeline.energy_max = json.loads(DEFAULT_ENERGY.read_text(encoding="utf-8"))["energy_max"]

    if args.image:
        print(json.dumps(pipeline.predict_path(args.image), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
