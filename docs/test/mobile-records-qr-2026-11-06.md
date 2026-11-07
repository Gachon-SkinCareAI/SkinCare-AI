# 모바일 경과 기록·기본 서비스·QR 배포

2026-11-06 작업. 이전 설치 버전은 9이며 기존 사용자·비회원 자료와 기본 AI 모델을 보존한다.

## 구현

- 신규 상처/경과 사진: 분석 전에 사진 재선택과 실제 촬영일 입력. 저장된 원본을 덮어쓰지 않고 경과 사진을 같은 `caseId`로 추가한다.
- 경과 저장: 촬영일, 통증 0~10, 붓기·진물(미기록/없음/있음/잘 모르겠음), 메모를 분석 기록에 저장하고 재조회한다. 이 값은 문진 결과를 재계산하지 않는다.
- 비교: 같은 상처의 전체 페이지를 조회해 촬영일을 선택하고 두 사진·증상을 비교한다. 같은 날의 여러 사진은 순번으로 구분한다. 인증 사진은 1~3배 확대 및 스크롤이 가능하다. 면적·회복률을 생성하지 않는다.
- 백과: 상처 4종·피부 질환 3종의 검색, 분류 필터, 상세 화면, 공식 원문 링크. 일반 정보 요약이며 전문가 검수 및 개인별 처치 지침으로 제공하지 않는다. 백과 피부 질환은 AI 사진 분류 클래스가 아니다.
- 병원·약국: 기존 실제 공공데이터/GPS 검색 유지. 별도의 ‘예시 지도 체험’에서 가상 기관 지도·목록·필터·검색을 제공한다. 예시에서는 GPS를 사용하지 않는다.
- 마이: 실제 회원 프로필과 기존 동의·탈퇴·로그아웃 유지. 회원 문의 초안을 계정별로 기기 암호화 저장하고, 명시적 접수 후 서버 문의·관리자 답변을 조회한다. 웹 초안은 메모리만 사용한다.

## 호환성 및 데이터

- 추가 마이그레이션: `V008__capture_date_and_symptoms.sql`. `captured_on`, `swelling`, `exudate`는 nullable이다. 과거 사진의 촬영일을 추정하여 채우지 않는다.
- `POST /api/wc/analyses` multipart에 선택적 `capturedOn` 추가.
- 기존 progress 요청에 선택적 `capturedOn`, `swelling`, `exudate` 추가. 필드 생략 시 기존 메타데이터를 유지한다. 날짜 빈 문자열은 촬영일 지우기, `UNRECORDED`는 증상 미기록으로 되돌리기다.
- 상세·목록·비교 응답에 `capturedOn`, 상세·비교의 progress에 두 증상을 추가한다. 기존 키와 이미지·문진 인터페이스는 유지한다.
- 날짜는 실제 달력 날짜 및 미래 여부를 검증하며, 권한·버전 충돌·재시도 검사는 계속 적용한다. 보관 기산점 `created_at`은 변경하지 않는다.
- 문의 초안은 접수 전 서버로 보내지 않는다. 계정 해시별 SecureStore 조각과 마지막 커밋 포인터로 보호하며 중단된 저장은 이전 초안을 유지한다. 탈퇴 시 기기의 초안도 제거한다.

## 검증 기록

- 백엔드: `gradlew.bat test bootJar --offline --console=plain` — 108개 중 107 통과, 실제 AI 연결용 조건부 테스트 1개 제외, 실패 0. H2 통합 테스트와 mock AI를 사용한다.
- 모바일: `npm test` — 13개 통과. 날짜, 위험도, API 계약, 미기록 증상, 백과 검색, 계정 분리 및 초안 중단 저장 복구 포함.
- 화면: Expo 웹 export + `SKINCARE_TEST_RISK_LEVELS=1 SKINCARE_TEST_SERVICES=1 node tests/guest-flow.browser.cjs` — 15개 검사 통과, 페이지 오류 0. 네트워크는 모두 합성 API로 격리한다. 실제 소셜 인증·Android 기기 검증을 대체하지 않는다.
- 배포 도구: `python -B -m unittest discover -s deploy/public-demo -p 'test_*.py'` — 52개 통과. QR 모듈은 `requirements-distribution.txt`, 디코딩 검사는 기존 AI 환경의 OpenCV 사용.
- 실제 MySQL: 별도 QA DB의 7→8 업그레이드에서 원본 바이트·등록일·메모 보존, 과거 촬영일 NULL, 새 증상 왕복 저장, 마이그레이션 재실행 검사를 통과했다.
- 시연 DB: 검증된 백업 후 V008 적용. 사용자 4명, 분석 6건, 답변 40건, 진단 0건으로 적용 전후 동일. AI·모델 파일 61개의 SHA-256을 검사해 변경 없음을 확인했다.

## QR 배포 절차와 현재 제한

서명된 APK를 전용 `/download/` 설치 페이지에서 배포한다. 공개 디렉터리는 설치 HTML, APK, QR PNG, 공개 버전 메타데이터만 포함한다. 개인키·DB·사진·환경 설정은 포함하지 않는다. 인증/AI API를 새로 공개하지 않는다.

1. 기존 서버 포트 정리 및 서비스 업데이트: `Update-And-Start-Services.cmd`.
2. 새 주소가 준비되면 APK 빌드·이전 서명 대조·내장 주소/기능 확인 후 QR 생성: `Build-And-Prepare-QR.ps1`.
3. `Publish-Install-QR.cmd`로 현재 터널에 게시. 실제 공개 APK 전체를 내려받아 SHA-256이 일치해야 완료 처리한다.
4. 실제 Android에서 QR→다운로드→데이터 보존 업데이트→기본 기능 재확인.

임시 터널 주소가 바뀌면 앱 내장 API 주소·QR·소셜 콜백을 다시 맞춰야 한다. 이전 호스트용 설치 파일은 새 호스트에서 제공하지 않도록 제한했다. 버전 10 APK(46,177,752바이트)는 이전 버전과 같은 서명, 내장 주소, 기능·권한 등 21개 검사를 통과했다. 생성된 QR의 디코딩도 확인했다. 2026-11-06 15:01 KST에 공개 설치 페이지·메타데이터·QR 일치를 확인했으며, 호스트 게시 검사에서 공개 APK 전체의 SHA-256 대조도 통과했다. 같은 날 사용자가 버전 10 설치 후 앱이 정상적으로 열린다고 확인했다. 이는 사용자 보고에 따른 설치·실행 확인이며 ADB 확인이나 새 기능 전체 검증을 뜻하지 않는다. 촬영일·증상 저장/재조회와 비교·확대 등 실제 휴대전화 기능 검증은 다음 단계다. 최신 상태는 작업 산출물의 `phone-v10-user-confirmation.json`, `public-download-verification.json`, `apk-v*-verification.json`, `distribution-candidate.json`, 서버의 `apk-distribution.json`으로 확인한다.

현재 설치 페이지: https://looksmart-longer-flashing-forestry.trycloudflare.com/download/

공개 APK SHA-256: `a4e3e54bdb7076a0d3722d34747d0169f3ddee3c6702de45e02d470f33ff00e7`.

Android 공식 웹 배포 안내: https://developer.android.com/distribute/marketing-tools/alternative-distribution

백과 출처: NHS의 Cuts and grazes, Burns and scalds, Acne, Atopic eczema, Contact dermatitis 및 미국 국립의학도서관 MedlinePlus Bruises. 각 항목의 URL과 확인일은 `mobile/src/data/encyclopedia.js`에 저장했다.
