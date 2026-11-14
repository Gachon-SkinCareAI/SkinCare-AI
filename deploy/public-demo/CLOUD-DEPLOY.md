# SkinCare AI 클라우드 배포 인수인계

공개 주소는 [skincare-ai.skincare-server.workers.dev](https://skincare-ai.skincare-server.workers.dev/download/)입니다. 이 배포 경로는 Cloudflare Worker와 Quick Tunnel을 사용하는 데모 구성입니다. Cloudflare는 Quick Tunnel을 테스트·개발용으로 안내합니다. 상시 운영에는 정식 공개 진입점이 필요합니다.

## 재완 → 건우

1. `cloud.env.example`을 복사해 배포 폴더의 `.env`로 전달합니다. 공개 호스트와 관리자 15분·사용자 60분 설정이 들어 있습니다.
2. 실제 OAuth 키, JWT 서명 키, DB 비밀번호, AI 서비스 토큰은 배포 폴더의 `private/`에 넣습니다. 기존 서비스 데이터를 복구할 때는 백업에 해당하는 DB 비밀번호와 유지보수 볼륨을 함께 사용합니다. 비공개 설정은 서버 운영자에게 승인된 비공개 경로로 전달합니다.
3. Worker 운영 환경에 등록한 `CONNECT_TOKEN` 및 `GATEWAY_TOKEN`과 같은 값이 들어 있는 `private/gateway-secrets.json`을 배포 폴더에 둡니다.

## 새 데모 서버 준비

Linux 서버에서 Docker Engine, Docker Compose 및 Python 3를 사용합니다. 문진에 로컬 Ollama를 사용한다면 기존 Qwen 모델과 GPU를 준비하고 NVIDIA Container Toolkit을 설치합니다.

1. 소스의 `backend`에서 `./gradlew bootJar`를 실행합니다. 이미 빌드한 JAR을 사용할 경우 배포할 소스 버전과 일치해야 합니다.
2. 저장소 루트에서 아래 명령으로 클라우드 배포 폴더를 만듭니다. `app-config.json`에는 AI 모델 원본 경로와 기존 비공개 소셜 설정이 있는 런타임 경로를 지정합니다. 이 명령은 빈 데모 DB를 위한 새 자격 증명을 생성합니다. 기존 데이터 이전에는 검증된 백업의 DB·유지보수 볼륨과 대응하는 자격 증명을 복원하는 절차가 따로 필요합니다.

   ~~~sh
   python3 deploy/public-demo/prepare_bundle.py \
     --mode cloud --local-app-config /private/app-config.json --output /srv/skincare
   ~~~

3. 배포 폴더에서 `private/`을 보호하고, 컨테이너 사용자 UID 10001이 읽어야 하는 파일을 설정합니다. 아래 명령은 rootful Docker의 기본 UID 매핑을 기준으로 합니다.

   ~~~sh
   cd /srv/skincare
   chmod 700 private
   sudo chown 10001:10001 private/application-secrets.properties private/ai-token.txt
   sudo chmod 400 private/application-secrets.properties private/ai-token.txt
   chmod 755 models
   chmod 644 models/detector.pt models/classifier.pt models/energy.json
   chmod 600 private/gateway-secrets.json
   python3 connect_cloud.py --check
   ~~~

4. `python3 connect_cloud.py`로 시작합니다. Ollama GPU 및 모델 볼륨을 준비한 구성에서는 `python3 connect_cloud.py --llm`을 사용합니다. 첫 기동은 MySQL 연결 → 유지보수 서비스의 V001~V008 적용 → Hibernate `ddl-auto=validate` → 공개 주소 연결 순입니다. 스키마 오류가 나면 maintenance 로그에서 실패한 마이그레이션과 DB 연결 대상을 확인합니다.
5. Caddy와 Cloudflare 커넥터는 Docker 내부망에서 통신합니다. 클라우드 Compose는 호스트 공개 포트가 0개인지 검사합니다. CORS와 OAuth 공개 기준 주소는 `.env`의 HTTPS 도메인 하나를 사용합니다. 컨테이너 안에서 자체 상태를 검사하는 루프백 주소는 호스트 포트 공개와 무관합니다.
6. 커넥터가 재시작돼 Quick Tunnel 주소가 바뀌면 실행기를 다시 실행해 Worker에 새 주소를 등록합니다. 외부 앱과 QR은 Worker 주소를 사용합니다.

## 소셜 로그인과 앱 배포

- 기존 Google·Kakao·Naver 앱의 클라이언트 ID를 사용합니다. 각 콘솔에 `https://skincare-ai.skincare-server.workers.dev/login/oauth2/code/google`, `.../kakao`, `.../naver` 중 해당 제공자의 콜백 URL을 등록합니다. 카카오·네이버에 사이트 도메인 등록 항목이 있으면 동일한 공개 도메인을 등록합니다.
- 기존 임시 주소가 앱에 들어 있다면 같은 패키지명과 서명으로 새 APK를 빌드하고 버전 코드를 올립니다. 설치 페이지, APK, QR, 배포 메타데이터를 배포 폴더의 `distribution/`에 둡니다. QR은 공개 주소의 `/download/`를 가리킵니다.
- 전환 완료는 실제 기기에서 소셜 로그인, 사진 업로드, 문진, 결과 조회까지 확인한 뒤 판단합니다.

[Cloudflare Quick Tunnel 안내](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
