SkinCare AI 약 5회 무료 시연 준비안
2026-11-03

사용자 요청 반영
AWS를 사용하지 않고, 약 5회 시연에 필요한 무료 구성을 우선한다.
skincare-ai-demo.com은 후보로 유지하며 이번 시연에서는 구매를 미룬다.
이전 AWS 제안은 history에 보존했으며 현재 실행안이 아니다.

무료 제안
휴대전화 앱 → 임시 HTTPS 주소(Cloudflare Quick Tunnel) → PC의 격리된 Docker 서비스
신규 서버 임대료와 도메인 구입비 없이 현재 PC의 AI·Spring·MySQL을 사용할 수 있다.
PC 전원·인터넷·Docker가 켜져 있어야 하며, 휴대전화는 USB 없이 접속할 수 있다.
Cloudflare 계정이 필요하지 않다. 임시 주소는 실행 중에만 유지되고 재시작 시 바뀐다.
주소 변경 시 소셜 콜백과 앱 API 주소를 다시 맞춰야 한다.
실제 소셜 로그인 3종 지원은 제공자 콘솔 등록 및 휴대전화 시험 전까지 확인되지 않았다.
정식 상시 운영이나 스토어 배포용 안정된 주소를 대신하는 구성은 아니다.

보존 및 다음 단계
기존 모델·DB·계정·QA 볼륨은 보존한다.
집 PC를 켜 두고 학교 휴대전화에서 접속하는 방식으로 확정했다.
별도 DB 실행·중지 파일은 outputs/free-demo-v1에 준비했다.
실제 공개 주소 발급, 소셜 콜백 등록, 공개용 APK 빌드와 LTE/5G 시험은 아직 진행 전이다.
PC를 꺼도 접근해야 한다면 이 방식 대신 무료 크레딧/학교 서버 등의 조건을 확인한다.

공식 근거
https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
https://developers.cloudflare.com/pages/how-to/preview-with-cloudflare-tunnel/
