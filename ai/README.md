# SkinCare AI 서버

FastAPI 서빙과 기존 학습/평가 도구를 함께 보관한다. 사용자 앱은 Spring Boot를 통해 이 서버를 호출한다.

| 경로 | 역할 |
| --- | --- |
| app/routers, app/schemas | API 라우팅과 요청/응답 계약 |
| vision | 품질 검사, 환부 탐지, 5개 유형 분류, UNKNOWN 및 파이프라인 |
| qa | 질문 은행, 규칙 엔진, 문진 흐름과 선택형 LLM 연동 |
| guide | 출처 포함 안내 카드와 검색 |
| tracking | 기록 비교 |
| jobs | 기존 재학습/피드백 작업 처리 |
| models | 모델 관련 자원; 실제 기본 가중치는 설정한 외부 경로 사용 |
| scripts | 기존 모델 변환·서빙 스모크 도구 |
| tests | API/문진/추론 등 회귀 테스트 |

현재 기본 추론은 기존 YOLO와 PyTorch ViT 가중치를 로드한다. `AI_DETECT_PATH`, `AI_CLASSIFY_PATH`, `AI_ENERGY_PATH`로 신뢰한 기존 파일을 지정한다. `scripts/export_onnx.py` 등의 존재가 ONNX 변환이나 서비스 전환 완료를 뜻하지 않는다. 자세한 보관 기준은 [models/README.md](models/README.md)를 참고한다.

`train_classify.py`와 `eval_classify.py`는 `vision/classify.py`가 가져오며 Docker 및 배포 스크립트도 루트 CLI 경로를 참조한다. 따라서 폴더 정리에서는 위치를 바꾸지 않는다. 학습/평가/데이터 감사 스크립트는 명시적으로 실행하기 전까지 학습하거나 데이터를 바꾸지 않는다.

기존 환경에서 API를 띄우려면 `ai` 폴더에서 `python -m uvicorn app.main:app`을 사용하고, 서버 간 인증/모델 경로는 기존 설정을 적용한다. 공개 시연은 `deploy/`의 검증된 실행 절차를 사용한다. 모델 후보의 기본 모델 승격은 별도 데이터 검수와 독립 평가 이후 판단한다.
