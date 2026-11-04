"""AI 서빙 진입점. 외부에는 8000을 열지 않는다."""

from __future__ import annotations

import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from app.config import settings
from app.middleware import RequestIdMiddleware
from app.routers import analyze, feedback, guide, health, predict, question, retrain, tracking
from vision.pipeline import DEFAULT_ENERGY, DEFAULT_DETECT, DEFAULT_CLASSIFY, VisionPipeline

ROOT = Path(__file__).resolve().parents[1]


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.settings = settings()
    app.state.inflight = 0
    pipeline = VisionPipeline(app.state.settings)
    energy_path = Path(os.environ.get("AI_ENERGY_PATH", str(DEFAULT_ENERGY)))
    if energy_path.exists():
        pipeline.energy_max = json.loads(energy_path.read_text(encoding="utf-8"))["energy_max"]
    try:
        started = time.perf_counter()
        pipeline.load(Path(os.environ.get("AI_DETECT_PATH", str(DEFAULT_DETECT))),
                      Path(os.environ.get("AI_CLASSIFY_PATH", str(DEFAULT_CLASSIFY))))
        logging.getLogger("skincare.performance").info(json.dumps({"event": "vision_load",
            "device": pipeline.device.type, "elapsed_ms": round((time.perf_counter() - started) * 1000, 2)}))
        if os.environ.get("AI_VISION_WARMUP") == "1":
            pipeline.warmup()
    except Exception as error:
        logging.getLogger("skincare.performance").error(json.dumps({
            "event": "vision_prepare_failed", "error_type": type(error).__name__}))
        pipeline.detector = None
        pipeline.classifier = None
    app.state.pipeline = pipeline
    yield


app = FastAPI(title="SkinCare AI", lifespan=lifespan)
app.add_middleware(RequestIdMiddleware)
app.include_router(predict.router, prefix="/api/v1")
app.include_router(question.router, prefix="/api/v1")
app.include_router(analyze.router, prefix="/api/v1")
app.include_router(guide.router, prefix="/api/v1")
app.include_router(tracking.router, prefix="/api/v1")
app.include_router(feedback.router, prefix="/api/v1")
app.include_router(retrain.router, prefix="/api/v1")
app.include_router(health.router, prefix="/api/v1")
