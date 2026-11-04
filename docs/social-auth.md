# 소셜 로그인 전용 인증

일반 사용자는 구글·카카오·네이버로 로그인/가입한다. 최초 소셜 인증은 약관 미동의 계정을 만들며, 기존 약관·개인정보 처리방침 버전에 명시적으로 동의한 뒤 분석과 기록 기능을 이용한다. 관리자 아이디·비밀번호 인증은 별도 유지한다.

## 구현과 데이터 보존

- 제공자와 제공자 고유 ID의 SHA-256을 조합해 사용자를 식별한다. 이메일은 선택적인 프로필 정보이며 계정 연결 키가 아니다. 카카오/네이버에서 이메일이 없더라도 가입한다.
- 기존 `google_<sub>` 사용자는 정확히 일치하는 Google subject로만 기존 ID와 기록을 이어 쓴다. 기존 LOCAL 계정/암호 해시/기록은 삭제하지 않지만 LOCAL 로그인과 과거 LOCAL JWT는 사용할 수 없다. 기존 LOCAL 기록을 새 소셜 계정으로 옮기는 기능은 구현하지 않았다. 같은 이메일을 이용한 자동 연결도 하지 않는다.
- `V003__social_only_identity.sql`은 이메일의 NOT NULL/UNIQUE를 제거하고 provider와 social_subject_hash의 UNIQUE를 추가한다. V001/V002는 수정하지 않는다. MySQL 개발 실행기는 최초 V003 적용 전에 기존 versioned DB를 백업한다. 미버전 legacy DB는 기존 관리 도구가 거부한다.
- 인증 완료 주소에는 JWT 대신 60초 일회용 코드만 보낸다. 코드는 처음 요청한 앱의 verifier로만 교환되며 재사용할 수 없다. OAuth state는 Spring의 서버 세션으로 검증하고 성공/실패 후 세션을 폐기한다. 서비스 API는 세션 쿠키로 인증하지 않고 기존 JWT만 받는다.
- 브라우저는 이동 중 verifier만 sessionStorage에 임시 보관하고 콜백 처리 즉시 제거한다. 웹 JWT는 메모리, 네이티브 JWT는 기존 SecureStore에 둔다. 제공자 access/refresh token은 저장하지 않는다.
- 현재 흐름 저장소는 최대 1,000개씩의 만료되는 인메모리 저장소다. 서버 재시작 시 진행 중 로그인은 다시 시작해야 한다. 다중 서버 배포 전 공유 세션/일회용 코드 저장소와 원자적 소비를 별도로 구현해야 한다.

## 제공자 설정

백엔드 환경변수 또는 로컬 개발 인스턴스의 `private/social-config.json`을 사용한다. 예시는 `deploy/mysql/social-config.example.json`이다. 예시 파일에 실제 키를 쓰지 말고 접근 제한된 private 폴더에 복사한 파일만 편집한다. 키는 프런트엔드 번들이나 저장소에 넣지 않는다.

| 제공자 | 서버 환경변수 | 등록할 기본 콜백 |
| --- | --- | --- |
| 구글 | GOOGLE_CLIENT_ID, GOOGLE_SECRET | http://127.0.0.1:9800/login/oauth2/code/google |
| 카카오 | KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET | http://127.0.0.1:9800/login/oauth2/code/kakao |
| 네이버 | NAVER_CLIENT_ID, NAVER_CLIENT_SECRET | http://127.0.0.1:9800/login/oauth2/code/naver |

구글은 Web application OAuth 클라이언트와 동의 화면/시험 사용자를 등록한다. 카카오는 REST API 키를 client ID로 사용하고 Client Secret을 활성화하며 카카오 로그인과 닉네임 동의 항목을 설정한다. 네이버는 네이버 로그인 애플리케이션의 서비스 URL/Callback URL과 이용 가능한 프로필 항목을 설정한다. 서비스 공개 전 각 제공자의 앱 검수/공개 상태도 확인해야 한다. 이메일을 필수 항목으로 요구하지 않는다.

`SOCIAL_PUBLIC_BASE_URL`은 제공자가 돌아올 **백엔드 주소**다. OAuth 시작 요청 주소(앱의 EXPO_PUBLIC_API_URL)와 scheme/host/port가 같아야 세션 state 쿠키가 돌아온다. 프록시 Host 헤더로 콜백을 만들지 않는다. 기본값은 http://127.0.0.1:9800이다.

`SOCIAL_REDIRECT_URIS`는 서버 인증 완료 후 **앱으로 돌아갈 주소**의 정확한 허용 목록이다. 기본값: `http://127.0.0.1:8081/,http://localhost:8081/,skincareai://oauth/callback`. 임의 쿼리/fragment/와일드카드를 허용하지 않는다. 배포 웹 주소를 추가하면 `SKINCARE_WEB_ALLOWED_ORIGINS`도 그 웹 origin으로 설정한다. 서버는 HTTPS와 로컬 loopback HTTP만 허용한다.

실물 휴대전화는 PC의 127.0.0.1에 접근할 수 없다. 같은 공개 HTTPS 백엔드 주소를 앱과 SOCIAL_PUBLIC_BASE_URL, 세 제공자의 콜백에 등록하고 `skincareai` scheme을 포함한 네이티브 개발 빌드를 사용해야 한다. Expo Go 대신 개발/설치 빌드에서 실제 기기의 브라우저 복귀를 검증한다.

키가 없는 제공자는 버튼이 비활성화된다. 이메일/비밀번호 또는 시험 계정으로 로그인하는 우회 경로는 없다. 실제 약관 문서는 서버의 `TERMS_POLICY_URL`, `PRIVACY_POLICY_URL`에 연결하거나, 관리 실행기의 `SKINCARE_BUILTIN_POLICIES=true`로 운영 정보가 반영된 내장 문서를 연결해야 최초 가입 동의를 마칠 수 있다. 앱은 서버 문서·버전을 조회한다. 확정 문서가 없을 때는 [문서 초안](api/policy-documents.md)을 읽을 수 있지만 가입 동의를 활성화하지 않는다. 테스트용 정책 URL/동의는 개발 번들에 넣지 않는다.

## API

- GET `/api/wc/auth/social/providers`: 세 제공자의 id/label/enabled.
- POST `/api/wc/auth/social/start`: provider, redirectUri, codeChallenge(S256 base64url) → authorizationPath.
- GET authorizationPath: 시스템 브라우저에서 열어 OAuth state 세션 시작.
- 콜백 fragment `oauth_code` → POST `/api/wc/auth/social/exchange`: code, codeVerifier → 기존 userId/nickname/token/consentRequired 응답.
- `oauth_error`, 취소, 만료, 서버 재시작은 재로그인 안내. 잘못된 state/검증되지 않은 subject로 계정을 만들지 않는다.
- 기존 일반 로그인·가입·이메일 인증·비밀번호 초기화/변경 API는 410. 기존 분석·문진·기록·문의 API 계약은 유지한다.

## 검증 범위

리포지토리 테스트는 세 제공자의 HTTP 토큰/프로필 응답을 모의하여 state, Naver token state, 이메일 누락/중복, 최초 동의, 기존 Google 계정 보존, 비활성 계정 거부, 일회용 코드/만료/verifier, LOCAL 인증 차단을 검증한다. 이는 실제 제공자의 등록/심사 또는 실물 휴대전화 인증 성공을 뜻하지 않는다. 별도 로컬 브라우저+MySQL 검증 산출물은 작업 폴더 `outputs/social-auth-v1`에 기록한다.

공식 문서:
- Google 서버 OAuth: https://developers.google.com/identity/protocols/oauth2/web-server
- Kakao REST 로그인: https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api
- Naver 로그인 API: https://developers.naver.com/docs/login/api/api.md
- Expo WebBrowser: https://docs.expo.dev/versions/latest/sdk/webbrowser/
- Spring Security authorization grants: https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html
