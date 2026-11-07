# 개발 저장소 서버 실행·배포

이 폴더의 서버 코드는 `C:\Users\kirit\SkinCare-AI`를 참고해 개발 저장소로 이식했습니다.
원본 저장소는 수정하지 않습니다. 개발 저장소의 `mobile/`은 후속 요청에 따라 공개 API에
연결하고 v13 APK로 배포했습니다. 공개 서비스는 Spring Boot + MySQL +
FastAPI + Caddy 구조이며 `public-demo/`가 현재 배포 경로입니다.

## 기존 공개 서버 갱신

현재 공개 설치 주소와 QR을 유지하려면 새 서버나 DB를 만들지 말고, 검증된 배포 후보를
`public-demo/update_develop_services.py`로 적용합니다. 기존 Docker Desktop과
무료 공개 서버 실행 창을 켜 둔 상태에서 제공된 `UPDATE-SERVER.cmd`를 실행하세요.
이 스크립트는 다음 순서로 동작합니다.

1. 후보 SHA-256, 기존 파일, Compose 프로젝트·DB 볼륨, 공개 주소를 확인합니다.
2. 기존 AI·백엔드 이미지를 보관하고 새 이미지를 빌드합니다.
3. 잠시 접속을 닫고 DB 백업을 확인한 뒤 AI·백엔드만 교체합니다.
4. AI 상태와 관리자 모듈, 공개 설치 페이지를 검사한 후 같은 주소를 사용합니다.
5. 실패하면 이전 소스·이미지 복원을 시도하며, 복원 실패 시 접속을 닫아 둡니다.

이번 업데이트는 DB 스키마·계정·모델 가중치·API 키·APK·QR을 바꾸지 않습니다.
기존 배포가 후보를 만든 이후 변경되었다면 중단합니다. 이때 검사를 건너뛰지 말고
새 기준으로 후보를 다시 준비하세요. 결과는 기존 공개 서버 출력 폴더의
`develop-upgrade.json`에서 확인합니다.

## 교체 성공 후 이전 서버로 복원

Docker Desktop과 기존 공개 서버 실행 창을 유지하고 제공된 `ROLLBACK-SERVER.cmd`를
실행하면 이번 교체 바로 전 서버로 돌아갑니다. `CHECK-ROLLBACK.cmd`는 서비스를
멈추지 않고 백업 파일과 저장된 Docker 이미지만 확인합니다.

- 새 업데이트 도구가 교체 전에 `develop-upgrade/before-.../rollback.json`을 만듭니다.
  이 기록은 검사 결과와 분리되어 있어 사전 검사를 다시 해도 사라지지 않습니다.
- 복원할 파일·이미지와 현재 배포 상태를 검사한 후 AI·백엔드만 이전 버전으로 되돌립니다.
  재빌드나 이미지 다운로드 없이 저장한 이미지의 고정 ID를 사용합니다.
- AI·백엔드 상태를 확인하고 같은 공개 주소를 다시 열어 관리자·설치 페이지를 검사합니다.
- 이후 쌓인 DB 데이터는 유지합니다. DB 백업을 과거 시점으로 덮어쓰거나 모델·APK·QR을
  교체하지 않습니다. 복원 실패 시 공개 접속을 닫고 `develop-rollback.json`에 결과를 남깁니다.
- 아직 교체하지 않았거나 백업/이미지가 없으면 중단합니다. 이후 다른 버전으로 배포하여
  파일·스키마·설정이 달라졌다면 그 변경을 덮어쓰지 않고 중단합니다.

실행 중인 서비스를 대상으로 한 교체·복원은 별도 실행이 필요합니다. 코드 테스트에서는
Docker 명령을 대체한 환경으로 성공·실패·변조·재시도 흐름을 검증했습니다.

`trycloudflare.com` 주소는 임시 주소입니다. 실행 창이나 터널을 종료·재생성하면
주소가 달라질 수 있습니다. 장기 운영 도메인은 별도 DNS/TLS 설정이 필요합니다.

## 새 환경에 배포할 때

- `backend/.env.example`: DB, JWT, AI 토큰, 정책·소셜 로그인 설정 예시.
- `deploy/mysql/manage.py`: 버전별 MySQL 스키마와 백업·복원 관리.
- `backend/src/main/resources/db/mysql/`: V001~V008 마이그레이션.
- `public-demo/prepare_bundle.py`: 필요한 소스·JAR·모델만 별도 비공개 런타임으로 복사.
- `public-demo/compose.yaml`: 외부 공개는 Caddy, DB와 AI는 내부 네트워크.
- `public-demo/apk_distribution.py`, `publish_apk.py`: 설치 페이지·APK·QR 패키징과 게시.
- `public-demo/publish_existing_install.py`: 이미 설정된 동일 공개 주소에 검증된 새 APK를
  게시합니다. 기존 디렉터리 마운트·서명·버전·전체 다운로드를 검사하며 서버를 재시작하지
  않습니다. 실패하면 이전 설치 파일을 복원합니다. 기존 서버와 현재 서버의 앱 API 코드가
  유지되는지 확인한 뒤 서버 복원 기록도 갱신하므로, 서버 복원 시 새 APK를 유지합니다.
  최초 설치 경로 설정에는 기존 `publish_apk.py`가 필요합니다.

DB 스키마를 적용한 후 `mysql` 프로필을 사용합니다. 운영 DB에 `ddl-auto=create`나
`update`를 적용하지 않습니다. 기존 `deploy/docker-compose.yml`은 과거 PostgreSQL/
Redis 실험용이며 현재 공개 서비스 실행 파일이 아닙니다.

## 앱/API 연결 범위

- 공개 앱 API는 `/api/wc/analyses`, `/api/wc/auth/guest`, `/api/wc/auth/social` 및
  기록·위치·문의·동의 관련 엔드포인트를 사용합니다. 상세 계약은 컨트롤러와 테스트에 있습니다.
- 이전 `/api/wc/diagnoses` 등은 `skincare.legacy.controllers-enabled=true`일 때만
  켜지는 호환 코드입니다. 공개 배포에서는 기본 비활성 상태를 유지합니다.
- 관리자 실제 운영 화면은 `/admin/`이며 ADMIN 인증을 요구하는 API를 호출합니다.
- 현재 QR은 `SkinCare-AI-develop/mobile`에서 빌드한 v13 APK를 제공합니다.
  개발 프론트의 인트로·홈·촬영·기록 디자인에 실제 인증·분석·문진·기록 API를 연결했습니다.
  기존 앱과 패키지·서명이 같아 삭제 없이 업데이트할 수 있습니다. 모바일 실행·검증 방법은
  `mobile/README.md`를 참고하세요. 휴대폰 카메라·GPS·실제 소셜 인증은 기기에서 확인해야 합니다.
- 소셜 제공자 키, 정책 문서 URL, 공공 의료기관 키는 기존 비공개 서버 설정을 사용합니다.
  설정 여부와 추가 작업은 관리자 화면의 '서비스 연결 준비 상태'에서 확인합니다.

## 검증

```powershell
cd backend
.\gradlew.bat test bootJar
cd ..\ai
python -m pytest -q -p no:cacheprovider tests
cd ..\admin-web
npm ci
npm run build
npm test
npm run test:operational
```

운영 화면 브라우저 테스트에는 Playwright Chromium 또는 `CHROMIUM_PATH`로 지정한
Chrome이 필요합니다. 배포 도구 테스트는 `qrcode`, Pillow, OpenCV가 설치된 Python에서
`deploy/public-demo` 디렉터리의 `python -m unittest discover -p "test_*.py"`로 실행합니다.
