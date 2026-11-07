# 비회원 우선 분석 구현·검증 기록

대상: `C:\Users\kirit\SkinCare-AI`의 로컬 코드. GitHub push/원격 병합은 실행하지 않았다. 기존 운영 DB·사진·계정·모델·학습 데이터는 변경하지 않았다.

## 사용자 흐름

앱 실행 → 사진 우선 홈 → 촬영/갤러리 → 비회원 서비스·건강정보 필수 동의 및 학습용 이용 선택 → 실제 분석 API → 문진 → 결과 → 기기별 기록 조회/삭제로 연결했다. 소셜 로그인은 마이 등에서 선택하며 Google·Kakao·Naver만 제공한다. 소셜 가입 때 GPS 동의를 별도로 선택할 수 있다.

현재 체크아웃의 모바일은 JavaScript 화면과 예시 기록을 사용하는 시연 상태였다. 기존 네이비 색상·Noto Sans KR·촬영 화면·하단 5개 탭을 유지하며 실제 API/PKCE 인증/보호된 이미지 로딩/기록 조회를 연결했다. 예시 기록과 계산되지 않은 회복 지표는 실제 기록으로 표시하지 않는다. 위험도는 결과 상단에 크게 표시하고 홈에서 119 연락을 열 수 있다.

## 변경 범위

- 모바일: `App.js`, `src/AppShell.js`, 홈, 분석/계정/의료기관 화면, 동의 UI, 서비스·보호 저장소, 네이티브 플러그인 설정 및 테스트.
- 서버: GuestController/GuestSessionService, GUEST 토큰 권한, 분석 소유자·학습 동의 스냅샷, 가입/GPS 선택 동의, 공개 동의 문서.
- DB: V006 비회원·위치·학습 필드, V007 현재 로컬 모듈 간 엔티티 불일치 보완. 이전 마이그레이션은 수정하지 않았다.
- 삭제/백업: 추가된 참조 데이터 삭제 순서, 보관 기간 처리, 복원 자료의 과거 학습 허용 해제.
- 기존 빌드 오류: DTO/엔티티/Repository 메서드 불일치를 호환 형태로 정리했다. 중복 병원·문의 경로와 별도 구형 분석/관리자 경로는 기본 실행에서 제외하고 원본 소스는 보존했다. `skincare.legacy.controllers-enabled`는 켜지 않는다. 현재 앱은 기존 검증된 `/analyses`, `/admin/operations`, `/admin/reviews` 등 계약을 사용한다.
- 관리자: 현재 React/Vite 소스를 빌드해 `/admin/`에 패키징하고 해시된 정적 자원 접근을 확인했다. 관리자 새 화면 전체 기능 검증을 이번 결과로 주장하지 않는다.
- 사진 생성 후 JPA 관리 객체 대신 이전 객체의 버전을 응답하던 오류도 수정했다. 문진 요청의 버전 충돌 회귀 테스트가 통과했다.

## 실행 및 검증

| 검사 | 실제 결과 |
| --- | --- |
| `backend/gradlew.bat test bootJar --no-daemon` | 102개 통과, 1개 조건부 제외, 실패 0개 |
| 별도 MySQL 8.0 인스턴스·QA DB | V001~V007 적용, Hibernate `validate`, 비회원 생성·동의·권한·삭제·보관 처리 성공 |
| MySQL 관리 백업 복원 | 사진 바이트 보존, 과거 학습 허용 해제, 백업 이후 삭제 요청 재적용 성공 |
| `mobile/npm test` | 5개 통과 |
| `python -B -m unittest discover -s deploy/mysql -p test_*.py` | 10개 통과 |
| `expo export --platform web --platform android` | 웹 번들 및 Android Hermes 번들 생성 성공 |
| Playwright 모바일 크기 화면 검사 | 5개 흐름 통과, 페이지 JavaScript 오류 0개 |

서버 테스트는 AI 호출을 대체한 합성 계약 테스트를 사용했다. 브라우저 테스트도 합성 사진·응답·OAuth 교환을 사용하며 실제 환자/계정/외부 서비스에 요청하지 않는다. AI 정확도 및 실제 소셜 로그인 성공을 새로 확인한 결과가 아니다. 실제 AI 연결 테스트 1개는 환경 조건상 실행하지 않았다.

브라우저 확인 범위: 첫 실행 홈·예시 기록 미표시, 필수 2개/선택 1개 동의, 비회원 사진→문진→결과→재조회, 학습 동의 철회 및 전체 삭제, 소셜 가입의 GPS 기본 미동의·동의 전 위치 호출 없음.

## 재현

1. `admin-web`과 `mobile`에서 각각 `npm ci`를 실행한다. Java 17과 Gradle wrapper를 사용한다.
2. `backend`에서 `gradlew.bat test bootJar --no-daemon`. 관리자 빌드가 서버 자원 처리에 포함된다.
3. `mobile`에서 `npm test`, `npx expo export --platform web --platform android --output-dir dist-guest-test`.
4. Playwright가 설치된 환경에서 `node tests/guest-flow.browser.cjs`. `PLAYWRIGHT_MODULE`로 기존 모듈 경로, `PLAYWRIGHT_BROWSERS_PATH`로 브라우저 캐시, `SKINCARE_WEB_EXPORT`로 번들 경로, `SKINCARE_TEST_OUTPUT`으로 결과 위치를 지정할 수 있다. 테스트는 로컬 주소를 가로채 합성 응답만 제공한다.

## 적용 전 남은 절차

공개 서버와 휴대전화에는 아직 배포하지 않았다. 다음 배포에서는 관리 도구로 기존 DB 백업을 먼저 만들고 V006/V007을 순서대로 적용한 뒤 서버를 시작해야 한다. `ddl-auto` 기본값은 `validate`이므로 실행만으로 실제 DB 구조를 자동 변경하지 않는다. 이미 운영 중인 DB에 V001을 다시 실행하거나 초기화하지 않는다. 스키마 이력이 없는 기존 DB는 관리 도구가 자동 변경을 거부한다.

현재 공개 HTTPS 주소를 `EXPO_PUBLIC_API_URL`에 지정하고 SecureStore·위치·WebBrowser 네이티브 플러그인이 포함된 새 APK를 빌드해야 한다. 기존 `mobile/android` 및 설치 APK는 이번 JS 번들 생성만으로 갱신되지 않는다. Android의 실제 키 보관/재실행, GPS 권한 거절, 소셜 인증 후 앱 복귀, LTE 사진 분석은 새 APK와 업데이트 서버로 별도 실기기 확인이 필요하다.

새 기본 모델·자동 학습·학습 데이터 반출은 실행하지 않았다. 동의 자료의 실제 학습 이용은 출처·라벨·중복·데이터 누수 검수 이후 별도 작업이다.
