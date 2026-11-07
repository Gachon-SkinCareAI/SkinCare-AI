# 비회원 현재 분석만 표시 — Android 버전 11

## 변경 범위

사용자가 요청한 비회원 표시 범위는 ‘과거 기록을 숨기고 현재 분석 결과만 표시’다. 버전 10은 이전 요구사항에 따라 기기 접근키로 비회원 기록을 다시 조회했으므로 홈에도 최근 분석이 표시되었다.

- 비회원 홈의 최근 분석과 과거 기록 목록을 숨긴다. 비회원 복원·업로드 완료·기록 탭에서 목록 API를 호출하지 않는다.
- 기록 탭은 현재 분석 이어보기와 새 분석·소셜 로그인·자료 관리 진입만 제공한다.
- 새 분석을 시작하면 이전 결과와 이전 상세 화면으로 돌아가는 경로를 제거한다. 앱 프로세스 재시작 시에도 현재 결과를 복원하지 않는다. 단순한 백그라운드 전환은 프로세스 재시작과 다르다.
- 회원의 최근 분석, 날짜별 경과 입력·비교 및 문의 기능은 유지한다.
- 비회원의 마이페이지에서 학습 동의 변경과 자료 전체 삭제를 계속 제공한다.

서버 API·DB·모델·기존 기록은 변경하지 않는다. 이전 버전 앱의 API 권한까지 차단하는 작업은 아니다. 필수 처리 동의 및 별도 선택 학습 동의를 유지하며, 과거 자료를 소급해 학습 허용 상태로 바꾸지 않는다.

## 변경 파일

| 파일 | 변경 |
| --- | --- |
| `mobile/src/AppShell.js` | 회원에게만 기록 조회·열기 허용, 비회원 현재 결과 및 탐색 상태 관리 |
| `mobile/src/screens/HomeScreen.js` | 최근 분석·회복 카드 표시를 회원 여부로 제어 |
| `mobile/src/screens/AnalysisScreens.js` | 비회원 현재 결과 안내, 회원만 경과 입력·비교 표시 |
| `mobile/src/screens/AccountScreens.js` | 비회원 자료 보관·삭제 및 화면 범위 안내 |
| `mobile/tests/guest-flow.browser.cjs` | 비회원 목록 요청 0회, 현재 결과·새 분석·회원 회귀 검증 |
| `deploy/public-demo/verify_public_apk.py` | 버전 11 이상 현재 분석 전용 UI 번들 확인 |
| `docs/api/guest-analysis.md` | 모바일 표시 범위와 서버 API 권한을 구분하여 명시 |

변경 전 모바일 5개 파일은 작업 폴더 `work/guest-current-only-v1/before/`에 보존했다. Android 생성 프로젝트는 기존 서명으로 버전 11을 빌드했다.

## 실행 및 결과

작업 폴더 기준: `C:\Users\kirit\Documents\Codex\2026-10-29\files-mentioned-by-the-user-skincare`.

| 실행 | 결과 |
| --- | --- |
| 모바일 `npm.cmd test` | 13개 통과 |
| Expo 웹 export (`EXPO_PUBLIC_API_URL=http://127.0.0.1:9800`) | 성공 |
| `node mobile/tests/guest-flow.browser.cjs` — 별도 export, `SKINCARE_TEST_RISK_LEVELS=1`, `SKINCARE_TEST_SERVICES=1` | 17개 검사 통과, 브라우저 오류 0개 |
| `Build-Public-Android.ps1 -VersionCode 11 -DetailedLog -ReuseNativeProject` | BUILD SUCCESSFUL, 352개 작업 중 32개 실행 |
| `verify_public_apk.py --version 11` | 22개 검사 통과, 버전 10과 서명 일치 |
| `apk_distribution.py` 및 QR 디코드 | 버전 11 설치 자산 생성, 기존 QR과 동일 |
| Windows PowerShell 게시 스크립트 파싱·Python 경로 확인 | 통과 |
| 도구의 Docker 접근 | named pipe 접근 거부 — PC에서 게시 실행 필요 |

브라우저 검사는 합성 API/이미지를 사용했다. 실제 계정·사진·DB를 이용한 검사나 실기기 확인을 대체하지 않는다. 뒤로가기 테스트의 버튼 이름을 실제 접근성 이름 ‘뒤로’로 수정한 후 전체 17개 검사가 통과했다. APK 빌드는 성공했지만 후속 Python 경로가 PowerShell 5에서 잘못 해석되어, 스크립트를 UTF-8 BOM으로 보완하고 생성된 APK를 별도로 검증했다.

## 배포 후보

- 앱 패키지: `com.skincareai.mobile`, 버전 코드 11, ARM64, 최소 Android SDK 24.
- 크기: 46,179,084 bytes.
- SHA-256: `de960106183aab4e0711faf5de7cf5d4217477305ac06d07a4b47902375b9702`.
- 서명 SHA-256: `d02225633c1cf740b374ed46e00d96dd049869730053019d1194c30ab7d6c201`.
- 서버: `https://looksmart-longer-flashing-forestry.trycloudflare.com`.
- 설치 페이지: 같은 서버의 `/download/`. 기존 QR을 그대로 사용한다.
- 게시 실행 파일: `outputs/guest-current-only-v1/Publish-Install-QR.cmd`.

현재 상태는 APK·QR 공개 게시 완료, 실기기 업데이트 확인 대기다. 전체 APK 다운로드 체크섬과 공개 버전 표시를 확인했다. 콜백 변경·DB 마이그레이션은 필요하지 않았으며 게시 과정에서 다운로드 경로를 제공하는 edge 컨테이너만 재생성했다.

실기기에서는 비회원 홈·기록에 과거 분석이 없고 현재 문진·결과는 표시되는지, 새 분석 후 이전 결과가 다시 열리지 않는지 확인한다. 회원으로 로그인하면 기존 경과 기록과 비교가 계속 가능해야 한다.

## 게시 중 인코딩 오류 후 보완

PC 실행에서 `UnicodeDecodeError`가 보고되었다. 기존 게시 도구는 사전 검사 실패를 상세 기록하지 않아 오류 파일을 확정할 수 없었다. 공개 메타데이터는 여전히 버전 10이며, 배포 상태 파일도 이전 버전 10의 완료 결과였다. 로컬에서 모든 사전 읽기·체크섬 검사를 실행해도 동일 오류는 재현되지 않았다.

`publish_apk.py`의 모든 JSON 읽기에 `utf-8-sig`를 지정해 Windows 기본 코드 페이지 의존성을 제거했다. PC 실행 스크립트에도 Python UTF-8 모드를 명시했다. 사전 검증만 실행하는 `--check-only`와 별도 시도 보고서 `--diagnostic-output`을 추가했다. 보고서는 실패 파일명·단계·코드 위치·인코딩·바이트 위치만 저장하며 입력 내용이나 토큰은 기록하지 않는다. APK 파일과 설치 자산은 변경하지 않았다.

- `python -B -m unittest test_apk_distribution -v`: 기존 검사 3개와 추가 검사 4개, 총 7개 통과.
- 추가 검사: UTF-8/BOM 파일의 CP949 환경 읽기, 한글 경로 사전 검사와 무변경 확인, 잘못된 인코딩의 안전한 실패 기록, 손상된 APK 게시 거절.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File outputs/guest-current-only-v1/Publish-Install-QR.ps1 -CheckOnly`: 실제 버전 11 자산으로 통과. Docker 호출이나 공개·런타임 파일 변경 없음.
- 실제 파일 검사 결과: `outputs/guest-current-only-v1/publication-preflight.json`.
- 다음 PC 게시 시도 결과: `outputs/guest-current-only-v1/publication-attempt.json`.

이 사전 검사만으로 공개 게시 성공을 확정하지 않고 이후 PC 실행 결과와 공개 파일을 별도로 확인했다.

후속 ‘complete’ 응답을 확인했으나 공개 `/download/release.json`은 버전 10이었다. 캐시 방지 쿼리에서도 버전 10이며 응답은 `Cache-Control: no-store`, `CF-Cache-Status: DYNAMIC`이었다. 2026-11-06 15:29경(KST) 이전 `outputs/mobile-records-qr-v1/distribution-candidate.json`과 공통 게시 결과가 갱신되었고, 버전 11 후보는 `published: false`, 전용 시도 보고서는 없었다. 따라서 이 완료 응답은 버전 10 재게시로 기록한다.

실행 파일 혼동을 줄이기 위해 `outputs/guest-current-only-v1/Publish-Version-11.cmd`를 추가했다. `VERSION 11`을 표시하고 후보 버전이 11인지 확인하며, 게시 후에도 버전 11의 전체 다운로드 검사가 통과했는지 확인한다. 새 진입점의 Windows PowerShell 사전 검사는 통과했고, 해당 파일의 PC 실행을 요청했다.

## 공개 게시 완료

2026-11-06 버전 11의 전용 시도 보고서에 `step: complete`, `passed: true`, `fullApkDownloadVerified: true`가 기록되었다. 게시 도구가 실제 공개 APK 전체를 내려받아 46,179,084 bytes 및 위 SHA-256과 일치함을 확인했다. 이어 공개 메타데이터·설치 페이지의 ‘버전 11’ 표시·QR 파일을 독립적으로 다시 확인했다.

`outputs/guest-current-only-v1/public-download-verification.json`에 확인 결과를 기록했다. 공통 설치·업데이트 CMD도 버전 11을 가리키도록 변경하고 이전 파일을 보관했다. 기존 QR과 공개 주소는 유지된다. 실기기 버전 11 설치 및 비회원 기록 숨김 확인은 사용자에게 요청한 상태이며 아직 완료로 기록하지 않는다.
