SkinCare AI 공개 서버·도메인 준비안
기준일: 2026-11-03 (한국시간)

상태
로컬 Linux 통합 검증은 완료했다. 아래 구성은 공개 서버 생성 전 준비안이며,
서버·고정 IP 생성이나 도메인 결제는 아직 실행하지 않았다.
AWS 계정 로그인 상태, 비용 선택, 도메인 이름을 확인한 뒤 실제 자원을 만든다.

권장 서버
- AWS Lightsail / 서울 ap-northeast-2
- OS Only → Ubuntu 24 / x86_64
- IPv4가 포함된 플랜: 2 vCPU / RAM 8GB / SSD 160GB
- 서버 이름: skincare-demo-01
- 고정 IPv4 이름: skincare-demo-ip (생성 후 해당 서버에 연결)
- 서버 요금표 기준 월 최대 USD 44. 세금·초과 사용료·선택적 부가 서비스는 별도.
- 기존 AI의 실제 클라우드 CPU 속도는 서버에서 별도 확인한다.
- MySQL·AI·Spring 서버는 같은 서버 내부 Docker 네트워크로 연결한다.

도메인 후보
- skincare-ai-demo.com
- 앱 API 및 소셜 콜백: https://api.skincare-ai-demo.com
- 관리자 웹: https://api.skincare-ai-demo.com/admin/
- 2026-11-03 등록기관 RDAP 조회: HTTP 404, 등록 기록 없음.
  실제 구매 가능 여부와 가격은 구매 시 콘솔에서 다시 확인한다.
- AWS의 .com 표준 등록·갱신 가격: 연 USD 16 (2026-07-01 이후 가격).
- Lightsail DNS를 쓰면 DNS zone 요금이 없고 월 300만 질의 범위가 포함된다.
  범위를 넘는 질의 등은 별도 요금이다.
- 서버 1개월 최대 요금 + 도메인 1년 표준 요금의 합은 USD 60, 세금·추가 사용료 별도.
- 도메인 자동 갱신 여부와 등록자 연락처는 구매 시 사용자가 확인한다.

첫 사용자 단계
1. AWS 계정이 없으면 https://aws.amazon.com/ko/ 에서 계정을 만들고,
   계정이 있으면 https://lightsail.aws.amazon.com/ 에 로그인한다.
2. 가입 과정의 본인 확인·결제 수단·비밀번호·인증코드는 직접 입력한다.
   이 값이나 AWS 액세스 키를 채팅에 전달할 필요는 없다.
3. 실제 서버 생성/도메인 구매 전 위 사양·요금과 도메인을 확정한다.
   계정의 무료 크레딧 적용 여부는 콘솔에서 확인하며 무료라고 가정하지 않는다.

서버 생성 후 연결 순서
1. 서울 리전에서 Ubuntu 24 OS Only, IPv4 포함 8GB 플랜으로 서버를 만든다.
2. Networking에서 고정 IP를 만들어 해당 서버에 붙인다.
3. IPv4 방화벽: TCP 80/443은 공개, TCP 22는 관리 PC의 공인 IPv4/32로 제한한다.
   IPv6 방화벽은 별도로 확인한다. 초기에는 IPv4 DNS만 쓰며 IPv6 SSH는 열지 않는다.
   MySQL 3306, AI 8000, Spring 9800, 관리 서비스 9100을 공인 포트로 열지 않는다.
4. SSH 키 파일은 PC의 비공개 위치에 보관한다. 파일 내용 대신 경로만 공유한다.
   최초 SSH 서버 지문은 AWS 콘솔의 브라우저 SSH에서 직접 확인한 값과 비교한다.
5. prepare-ubuntu.sh를 새 서버에 전송하고 다음을 순서대로 실행한다.
   bash prepare-ubuntu.sh --check
   sudo bash prepare-ubuntu.sh --apply
   --check는 읽기 전용이며 --apply는 Docker 공식 저장소에서 Docker/Compose를 설치한다.
   기존 DB·앱을 배포하거나 방화벽·SSH 설정을 바꾸는 스크립트가 아니다.

도메인 및 DNS
1. Lightsail → Domains & DNS에서 원하는 도메인을 검색하고 등록 비용을 확인한다.
2. 등록자 연락처와 이메일 확인을 사용자가 완료한다.
3. Lightsail DNS zone을 사용하고 A 레코드 api를 서버의 고정 IPv4로 연결한다.
   이미 다른 DNS 제공자를 쓰는 도메인이라면 그 제공자에서 api A 레코드만 추가한다.
   기존 이메일·다른 서비스의 DNS 기록을 지우거나 이름서버를 임의로 바꾸지 않는다.
4. 초기 IPv4 구성에는 api의 AAAA 레코드를 추가하지 않는다.
5. 실제 주소가 정해지면 다음 읽기 전용 검사로 DNS를 확인한다.
   python check_public_target.py --hostname 실제_API_도메인 --ipv4 실제_고정_IP

그다음 배포
부모 폴더의 prepare_bundle.py로 실제 도메인을 넣은 공개 패키지를 새 비공개 폴더에 만든다.
SSH로 전송 후 Caddy HTTPS, Spring, MySQL, AI를 기동하고 다음을 확인한다.
   python check_public_target.py --hostname 실제_API_도메인 --ipv4 실제_고정_IP --https
소셜 제공자의 공개 콜백 예시 (도메인 확정 후 적용):
   https://api.skincare-ai-demo.com/login/oauth2/code/google
   https://api.skincare-ai-demo.com/login/oauth2/code/kakao
   https://api.skincare-ai-demo.com/login/oauth2/code/naver
기존 PC·USB 테스트 콜백은 보존한다. 앱 복귀 scheme은 skincareai://oauth/callback이다.

검증 범위
check_public_target.py의 CLI 및 입력 거부 동작은 로컬에서 검사했다.
prepare-ubuntu.sh는 작성·정적 검토한 단계이며 실제 Ubuntu 서버 실행은 아직 하지 않았다.
이 PC에서는 Git Bash가 실행 권한 제한으로 시작하지 못해 bash -n 검증도 미완료다.
서버 생성 후 --check부터 실행하고, 실패 시 원인을 확인한 뒤 설치를 진행한다.
DNS/TLS 점검 통과만으로 실제 소셜 로그인이나 상처 분석 전체 동작이 검증되지는 않는다.

공식 근거
서버 요금: https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html
서울 리전: https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-regions-and-availability-zones-in-amazon-lightsail.html
Ubuntu 이미지: https://docs.aws.amazon.com/lightsail/latest/userguide/compare-options-choose-lightsail-instance-image.html
방화벽: https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-firewall-rules-reference.html
도메인 등록: https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-register-new-domain.html
도메인 요금: https://d32ze2gidvkk54.cloudfront.net/Amazon_Route_53_Domain_Registration_Pricing_20140731.pdf
DNS 요금: https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html
Docker 설치: https://docs.docker.com/engine/install/ubuntu/
