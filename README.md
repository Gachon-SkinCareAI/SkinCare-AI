# SkinCare-AI
Gachon University graduate project

상처 사진을 AI로 분석하고 문진을 거쳐 대처 방법과 주변 병원·약국을 안내하는 앱입니다.

- 앱 설치: https://skincare-ai.skincare-server.workers.dev/download/
- 관리자 페이지: https://skincare-ai.skincare-server.workers.dev/admin/

## 폴더 구성

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot API 서버 |
| `mobile/` | 사용자 앱 (React Native / Expo) |
| `admin-web/` | 관리자 웹 (React) |
| `ai/` | 상처 분석 AI 서버 (YOLO + ViT)와 문진 엔진 |
| `deploy/` | 로컬 개발 DB와 데모 배포 설정 ([deploy/README-DEVELOP.md](deploy/README-DEVELOP.md)) |
| `docs/` | API, DB, 설계 문서 |

> 공개 저장소입니다. 서버 IP, SSH 키, `private/` 비밀값, DB 백업, 앱 서명 키는 커밋하지 마세요.
