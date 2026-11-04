"""사진 한 장을 품질·탐지·분류·게이트로 처리한다."""

from __future__ import annotations

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile

from app.deps import acquire_inflight, release_inflight, verify_token
from vision.preprocess import read_bytes

router = APIRouter(prefix="/ai", tags=["vision"], dependencies=[Depends(verify_token)])
MAX_BYTES = 8 * 1024 * 1024


@router.post("/predict")
async def predict(request: Request, file: UploadFile = File(...)):
    acquire_inflight(request)
    try:
        raw = await file.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise HTTPException(status_code=413, detail={"error": "image_too_large", "message": "이미지는 8MB 이하만 받는다."})
        if not request.app.state.pipeline.ready:
            raise HTTPException(status_code=503, detail={"error": "model_unavailable", "message": "모델을 불러오지 못했습니다."})
        image = read_bytes(raw)
        if image is None:
            raise HTTPException(status_code=415, detail={"error": "unsupported_media", "message": "이미지를 열 수 없습니다."})
        result = request.app.state.pipeline.predict_array(image)
        result["request_id"] = request.state.request_id
        return result
    finally:
        release_inflight(request)
