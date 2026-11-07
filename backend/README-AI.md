# AI 서버 연동

기존 진단·문진 서비스는 `external/AiProtocol.java`에서 FastAPI 계약으로 변환한다.

- 사진 분석: 중첩된 quality/detection/classification을 기존 DTO로 변환한다. 품질 실패는 재촬영 오류로 처리하고 OOD/게이트 실패는 UNKNOWN으로 유지한다. 정규화 박스는 원본 픽셀 좌표로 저장한다.
- 문진: last_question_id/answer를 전송한다. 한국어 선택지와 AI의 value를 함께 저장해 다음 답변을 변환한다. 시나리오는 사진 분류로 임의 확정하지 않는다.
- 위험도: vision.label/gate_passed를 전송하고 증상 메모는 user_text로 분리한다. AI는 기존 결정적 파서로 증상 메모를 검사한다. 미완성 결과는 UNKNOWN으로 저장하며 문진에서 보완한다.
- 재학습: reason은 FastAPI 계약에 따라 쿼리 파라미터로 보낸다.

## 설정

backend/.env의 AI_SERVICE_URL, AI_SERVICE_TOKEN, AI_READ_TIMEOUT_MS를 설정한다. AI_SERVICE_TOKEN은 ai/.env와 같아야 한다. 기본 응답 제한 시간은 30초이며 로컬 LLM 호출 제한 시간 20초를 포함한다.

모델 가중치는 ai의 AI_DETECT_PATH, AI_CLASSIFY_PATH, AI_ENERGY_PATH로 지정한다. 이번 변경은 가중치 배포나 DB 변경을 수행하지 않는다.

## 검증

JDK 17 이상과 프로젝트 Gradle을 사용한다.

```powershell
.\gradlew.bat test --tests "skincare.external.*"
```

Python 환경에서는 ai 폴더에서 다음을 실행한다.

```powershell
python -m pytest tests -q
```

실제 모델 테스트에는 ai/requirements.txt의 의존성이 필요하다. DB·로그인·모바일까지의 통합 검증에는 별도 실행 환경이 필요하다.
