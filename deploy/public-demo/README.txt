SkinCare AI 소규모 공개 시연 배포 준비
2026-11-03

이 구성은 현재 서비스의 Spring Boot, MySQL, FastAPI와 기존 YOLOv8s/ViT 가중치를 별도 Linux 서버에서 실행하기 위한 것이다.
현재 기본 모델을 재학습하거나 교체하지 않는다. 사용자 앱은 Android, 관리자는 /admin/ 웹을 사용한다.
컨테이너 검증 결과와 실제 공개 배포 성공은 구분한다. 도메인/서버가 확정되기 전에는 공개 배포 완료로 표시하지 않는다.

구성
Android 앱 → https://선택한-도메인 → Caddy → Spring Boot
Spring Boot → 내부 MySQL / FastAPI
공개 포트는 80/443만이다. MySQL 3306, Spring 9800, AI 8000, 관리 게이트 9100은 외부로 게시하지 않는다.
관리자 웹의 API는 기존 ADMIN 인증을 유지한다. AI 모델 파일은 읽기 전용 볼륨이며 시작 시 해시를 검사한다.
백엔드는 애플리케이션용 DB 권한(SELECT/INSERT/UPDATE/DELETE)으로 실행하고 스키마 자동 update 대신 validate를 사용한다.
OAuth 일회용 코드/세션이 메모리에 있으므로 현재는 Spring 인스턴스 1개만 실행한다. 다중 서버 확장은 별도 작업이다.

서버 제안과 비용
- 초기 후보: AWS Lightsail Linux x86_64, 2 vCPU / 8GB RAM / 160GB SSD, 서울 리전 지원 여부와 현재 콘솔 가격 확인.
- 공식 공개 IPv4 요금표상 월 최대 USD 44. 세금, 도메인, 선택적인 추가 백업 및 초과 전송은 별도다.
- AI CPU 지연은 해당 VM에서 실제 측정해야 한다. 로컬 CPU 결과로 클라우드 속도를 보장하지 않는다.
- https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html

1. 로컬 Linux 검증
prepare_bundle.py --mode qa --local-app-config <기존 비공개 app-config.json> --output <새로운 비공개 폴더>
생성 폴더는 기존 모델의 사본과 빌드된 JAR를 포함한다. 기존 DB/사진/사용자 계정/학습 데이터는 복사하지 않는다.
QA에는 새 임의 비밀값을 만들고 실제 소셜·의료기관 키는 넣지 않는다.
생성 폴더에서 python verify_host.py를 실행한다. 독립 Compose 프로젝트를 사용하고 127.0.0.1:19880만 연다.
검증 성공 시 QA 컨테이너를 중지하며 데이터 볼륨은 보존한다. 실패 시 로그와 격리된 상태를 남긴다.
개별 검증 내용은 verification.json, 현재 단계는 qa-status.json이다. 테스트 통과는 실제 공개 TLS나 로그인 검증을 대신하지 않는다.
2026-11-03 로컬 Linux 검증 통과: 5개 컨테이너 기동, 정책/관리자 웹, 비인증 접근 차단, 기존 모델 로딩/예측 API,
DB 마이그레이션/CRUD 권한/관리 백업 복원/삭제 기록 재적용/보관기한 dry run, 관리 서비스 장애 차단 및 복구.
예측 API 검사는 합성 입력으로 수행한 소프트웨어 확인이다. 실제 상처 분류 정확도나 클라우드 지연을 검증한 결과가 아니다.
Windows에서 생성한 초기 QA 비밀 파일의 CRLF는 MySQL의 파일 읽기 방식에 맞춰 호환 처리한다.
새 패키지는 LF 바이트만 기록한다. 기존 DB가 있는 비밀 파일을 임의로 줄바꿈 변환하거나 비밀번호를 바꾸지 않는다.
줄바꿈 회귀 검사는 python -B test_secret_files.py -v로 수행한다.

2. 공개 환경 결정
- 클라우드 계정과 서버, 소유한 도메인/서브도메인, TLS 이메일을 준비한다.
- DNS A 레코드를 서버의 고정 공인 IPv4에 연결한다. 사용하지 않는 AAAA 레코드는 잘못된 서버를 가리키지 않아야 한다.
- 클라우드 방화벽은 80/443, SSH 22는 운영자의 관리 IP 범위로 제한한다.
- 연구·시연용 대상, 실제 호스팅 업체/처리 환경, 개인정보 안내가 맞는지 확인한다. 전문 검수 전 의료적 위험도·케어는 연구·시연 범위를 유지한다.
- 과금 자원 생성과 도메인 구매는 이 코드가 자동 수행하지 않는다.

3. 공개 서버용 패키징
현재 소스를 검증해 backend JAR를 다시 만든 뒤 다음을 실행한다.
python prepare_bundle.py --mode public --hostname 실제도메인 --email 운영이메일 --local-app-config <기존 비공개 app-config.json> --output <새 비공개 폴더>
public 모드는 기존 소셜/의료기관 설정만 별도로 복사하고 DB 암호/JWT/내부 AI 토큰/초기 관리자 비밀번호는 새로 만든다.
private/application-secrets.properties에 초기 관리자 ID/비밀번호가 있다. 채팅, Git, Docker 이미지, 공개 다운로드 경로에 넣지 않는다.
이 비공개 배포 폴더를 인증된 SSH/SFTP로 서버에 전송한다. 기존 로컬 DB를 가져오지 않으며 공개 환경의 계정은 해당 서버에 새로 생성된다.
서버에 Docker Engine과 Compose v2.24.4 이상을 설치한다. QA에서 확인한 이미지 digest를 배포 기록에 남기고 고정한다.
docker compose --env-file .env config --quiet
docker compose --env-file .env build
docker compose --env-file .env up -d --wait
compose.qa.yaml은 공개 서버에서 사용하지 않는다.
공개 TLS 발급, /admin/ 로드, 개인정보 링크, 비인증 API 차단, 모델 상태, 재시작 후 기록을 별도로 확인한다.

4. 소셜 개발자 콘솔
기존 PC 콜백을 보존하고 다음 공개 콜백을 각각 추가한다.
Google: https://실제도메인/login/oauth2/code/google
Kakao: https://실제도메인/login/oauth2/code/kakao
Naver: https://실제도메인/login/oauth2/code/naver
제공자의 서비스 URL/개인정보처리방침 URL도 공개 주소로 설정하고 테스트 사용자/앱 공개 상태를 확인한다.
제공자→서버 주소와 서버→앱 주소는 다르다. 앱 복귀 주소는 기존 skincareai://oauth/callback을 유지한다.

5. 공개 HTTPS용 APK
JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME을 기존 Android 빌드 환경으로 설정한다.
Build-Public-Android.ps1 -ApiOrigin https://실제도메인 -SigningDirectory <저장소 밖 비공개 폴더> -OutputDirectory <산출물 폴더> -VersionCode 2
공개용 패키지는 com.skincareai.mobile이며 USB용 패키지와 다르다. 실제 서버의 제공자 3종과 정책 문서 준비를 먼저 확인한다.
배포 서명 키와 암호는 로컬 비공개 폴더에 보존한다. 같은 키를 분실하면 기존 설치 앱의 업데이트에 영향을 준다.
USB 앱과 공개 앱은 같은 복귀 scheme을 사용한다. 시험 기기에 둘 다 설치돼 있다면 Android의 앱 선택 화면에서 공개 앱을 선택한다.
기존 USB 앱을 자동 삭제하지 않는다. 필요 시 해당 앱의 기본 링크 설정을 해제하거나 사용자가 직접 정리한다.
먼저 서명 APK를 제한된 시연 대상에게 배포하고, USB를 뽑은 LTE/5G 환경에서 로그인 3종/촬영/문진/경과 기록을 확인한다.
Play Store 배포는 별도의 개발자 계정, AAB/Play App Signing, 스토어 자료·공개 심사 단계다.

보관·삭제와 복구
- 기존 Windows용 관리 코드의 SQL/삭제 목록/복원 필터를 Linux 어댑터에서 재사용한다.
- 첫 기동: DB identity 확인 → 버전 마이그레이션 → 보관기한 정리 → 초기 백업 → 게이트 준비.
- 실행 중 60초마다 최대 5년 보관기한과 삭제 요청을 확인하고 관리 백업을 교체한다.
- 관리 작업 중/실패/120초 이상 지연되면 HTTPS 요청은 가용하지 않은 상태로 응답한다. 삭제 작업 실패를 숨기고 서비스를 계속 공개하지 않는다.
- 하루 1회 백업하며 7일보다 오래된 일별 관리 백업은 새 백업 생성 성공 후 정리한다. 삭제 요청 발생 시에는 기존 관리 백업 전체에 삭제를 반영한다.
- 자체 관리 볼륨 범위다. 클라우드 디스크 스냅샷/외부 수동 사본에 자동 전파되지 않으므로 정책이 정해지기 전 자동 스냅샷을 별도로 켜지 않는다.
- 복원은 기존 DB를 덮어쓰지 않는 skincare_restore_* 대상으로만 수행하며 최신 삭제 목록을 재적용한다.
- 원본 DB 전체 손실, 서버 외부 백업, 실제 운영 전환은 별도 복구 절차가 필요하다. 현재 구성의 단일 서버 장애 한계를 기록한다.
- docker compose down -v는 DB/관리 기록을 삭제하므로 사용하지 않는다. 일반 중지는 docker compose stop이다.

공식 참고
https://caddyserver.com/docs/automatic-https
https://docs.docker.com/compose/how-tos/use-secrets/
https://hub.docker.com/_/mysql
https://docs.expo.dev/build/internal-distribution/
