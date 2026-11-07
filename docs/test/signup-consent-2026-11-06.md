# 첫 가입 전체 동의·위치 권한 요청 — Android 버전 12

## 구현

- 첫 가입 화면에 필수 이용약관·개인정보·건강정보와 선택 위치정보·AI 모델 개선용 이용을 표시한다. 모든 항목은 미선택으로 시작한다.
- 동의 항목 아래에 ‘전체 동의 (선택 항목 포함)’ 버튼을 둔다. 전체 해제·개별 변경이 가능하고, 선택 항목을 거절해도 필수 항목만으로 가입할 수 있다. 문서가 없는 항목은 선택할 수 없다.
- 필수·위치 동의를 서버에 저장하고 학습 선택을 별도 API에 저장한 뒤, 위치 동의가 있을 때만 네이티브 전경 위치 권한을 요청한다. OS 권한 거절·오류는 가입을 취소하지 않는다.
- 가입 과정에서는 좌표를 읽거나 서버에 보내지 않는다. 실제 좌표 조회는 병원·약국의 현재 위치 검색 시에만 실행한다. 웹 시연에서는 가입 중 브라우저 위치 권한을 요청하지 않는다.
- 마이페이지의 ‘선택 동의 관리’ 카드를 없애고, ‘개인정보 설정’ 버튼으로 별도 변경·철회 화면에 진입한다. 비회원 자료 삭제와 회원 탈퇴는 유지한다.
- 기존 가입 계정에는 첫 가입을 강제하지 않는다. 기존 동의·기록·비회원 과거 목록 숨김도 유지한다.

## 변경 파일

`mobile/src/screens/AccountScreens.js`, `mobile/src/AppShell.js`, `mobile/src/screens/HomeScreen.js`, `mobile/src/screens/MedicalScreen.js`, 새 `mobile/src/services/signup.js`에 구현했다. `mobile/tests/signup.test.mjs`, `mobile/tests/guest-flow.browser.cjs`, `deploy/public-demo/verify_public_apk.py`에 검증을 추가했다. 기존 백엔드 API와 DB 스키마, 모델 파일은 수정하지 않았다.

## 검증

| 명령·항목 | 결과 |
| --- | --- |
| 모바일 `npm.cmd test` | 18개 통과 |
| Expo 웹 export | 통과 |
| `node tests/guest-flow.browser.cjs` — 필수 동의만 선택 | 19개 검사 통과, 브라우저 오류 0 |
| 같은 브라우저 검사 — `SKINCARE_TEST_SIGNUP_ALL=1` | 19개 검사 통과, 브라우저 오류 0 |
| `Build-Public-Android.ps1 -VersionCode 12 -DetailedLog -ReuseNativeProject` | 빌드 성공 |
| `verify_public_apk.py --version 12` | 23개 검사 통과, 기존 앱과 동일 서명 |
| Windows PowerShell의 게시 `-CheckOnly` | 실제 APK·설정 사전 검사 통과 |

단위 검사는 서버 저장 후 권한 요청 순서, 필수만 선택한 경우의 권한 요청 없음, 권한 거절·오류 시 가입 유지, 서버 저장 실패 시 권한 요청 금지, 사용 불가 동의 선택 거절을 확인했다. 브라우저 검사는 전체·개별·전체 해제와 선택 상태의 API 전달, 별도 설정에서 철회, 회원·비회원 기록·사진 비교·문의 등의 회귀를 합성 API로 확인했다. 실제 Android OS 권한 창과 신규 실계정 가입은 아직 확인 전이다.

필수 동의와 학습 선택 저장은 기존 별도 API를 순서대로 사용한다. 학습 선택 저장이 실패하면 필수 동의는 이미 저장될 수 있지만, 앱은 실패를 표시하고 재시도를 요청한다. 선택 동의를 저장했다고 허위 안내하거나 기기 권한 요청으로 넘어가지 않는다.

## 설치 파일과 배포

- 패키지 `com.skincareai.mobile`, 버전 12, ARM64, 최소 SDK 24.
- 46,184,344 bytes.
- APK SHA-256: `a280e13d07d675b876d546f1b6b58787e9a5413353d7dbcad4900844c70c20a2`.
- 서명 SHA-256: `d02225633c1cf740b374ed46e00d96dd049869730053019d1194c30ab7d6c201`.
- 설치 주소: `https://looksmart-longer-flashing-forestry.trycloudflare.com/download/`.
- PC 게시 실행 파일: 작업 폴더의 `outputs/signup-consent-v1/Publish-Version-12.cmd`.

버전 11 APK와 변경 전 소스는 보존했다. 2026-11-06 PC 게시 결과에서 버전 12 `complete` 및 전체 공개 APK 다운로드 SHA-256 일치를 확인했다. 공개 메타데이터·버전 12 설치 페이지·QR도 별도로 확인했다. 공통 설치·업데이트 실행 파일은 검증된 버전 12를 가리킨다.

사용자가 ‘버전 12 설치·마이페이지 변경 확인’이라고 응답했다. `outputs/signup-consent-v1/phone-v12-user-confirmation.json`에 사용자 보고 근거로 기록했고, ADB로 확인한 것으로 표시하지 않았다. 첫 가입의 전체 동의 화면과 Android 위치 권한 요청은 실기기 미확인 상태다. 최초 가입·권한 창은 아직 가입하지 않은 계정으로 확인해야 하며 기존 계정이나 동의를 테스트 목적으로 지우지 않는다.
