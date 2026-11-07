# SkinCare AI 개발 프론트 — 공개 서버 연동

`SkinCare-AI-develop/mobile`의 인트로·홈·카메라·갤러리·기록 카드 디자인을 바탕으로
공개 서버를 연결한 Expo/React Native 앱입니다. 원본 `SkinCare-AI`는 수정하지 않습니다.

## 실행

```powershell
npm ci
npm start
```

`.env.example`의 `EXPO_PUBLIC_API_URL`을 `.env`에 설정할 수 있습니다.
기본값은 현재 공개 서버 주소입니다. 임시 터널 주소가 바뀌면 설정을 바꾸고 APK를 다시
빌드해야 합니다. API 토큰·OAuth 비밀키·서명 비밀번호는 소스나 `.env`에 넣지 않습니다.
웹 개발에서는 서버의 허용된 OAuth 복귀 주소를 사용해야 하며, APK는 `skincareai://oauth/callback`을 사용합니다.

## 연결한 기능

- 기존 인트로에서 소셜 로그인 또는 비회원으로 시작. 가짜 이메일 인증은 제거했습니다.
- 카메라/갤러리 선택 → 촬영일·동의 확인 → 사진 정규화 → 실제 업로드.
- 서버 문진 선택지·추가 설명 제출, 위험도·출처 표시, 실패 후 이어하기.
- 회원의 실제 기록 목록·필터·삭제, 통증·붓기·진물·메모 저장, 경과 사진 및 날짜별 비교.
- 비회원은 현재 분석만 조회. 과거 목록은 회원 기능이며 비회원 자료는 자동 이전하지 않습니다.
- 개인정보 동의 조회/변경, 계정/비회원 자료 삭제, 문의·답변, 병원·약국 검색, 백과.
- Native 세션은 SecureStore에 보관. 기존 v12와 같은 저장소 키·패키지·서명을 사용합니다.
- 회원가입/분석 동의는 기본 해제이며, 선택 동의 없이도 필수 흐름을 이용할 수 있습니다.

`services/`는 HTTP·사진·OAuth·기록 표시, `stores/`는 보호된 세션과 문의 초안,
`AppShell.js`는 세션/화면 흐름, `RecordViews.js`는 개발 프론트의 기록 화면을 담당합니다.
`data/homeDemo.js`는 기존 디자인 참고 자료로 남아 있지만 실제 앱에서 불러오지 않습니다.

## 검증과 Android 빌드

```powershell
npm test
npx expo export --platform web
```

`tests/guest-flow.browser.cjs`는 실제 계정에 요청하지 않는 합성 API 브라우저 검사입니다.
소셜 로그인 제공자 화면과 휴대폰 카메라/위치는 실제 기기에서도 확인해야 합니다.

공개 APK는 저장소의 `deploy/public-demo/Build-Public-Android.ps1`을 사용합니다.
기존 비공개 서명 디렉터리를 재사용하고 기존 APK보다 높은 versionCode로 빌드해야
설치된 앱과 기록을 유지한 채 업데이트할 수 있습니다. 빌드 산출물과 서명키는 커밋하지 않습니다.
