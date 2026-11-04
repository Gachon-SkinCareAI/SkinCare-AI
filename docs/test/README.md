# 구조 변경 검증

프로젝트 루트 기준 명령이다. 모델 재학습, 실제 계정 생성 또는 공개 서버 변경을 하지 않는 소스 검증부터 실행한다.

```powershell
npm --prefix mobile run typecheck
npm --prefix mobile test
npm --prefix admin-web run check
# Java 17과 Gradle 캐시를 준비한 뒤
.\backend\gradlew.bat -p backend test bootJar
```

관리자 `check`는 JS 구문과 상대 import 대상만 검사한다. 모바일 테스트는 API 요청, OAuth/보관 처리, 사진 전처리, 문진 표시 등의 기존 계약을 검사한다. React Native Web을 확인하려면 다음 명령을 사용한다. 테스트용 API 주소는 별도로 설정해야 한다.

```powershell
npm --prefix mobile run export:web
```

백엔드 일반 테스트는 테스트용 H2와 가짜 인증 설정을 사용한다. `AI_LIVE_TEST=true` 조건의 실제 AI 연결 테스트는 별도 환경이 필요하며 일반 구조 검증에서 실행하지 않는다. `bootJar` 안의 `BOOT-INF/classes/static/admin/`에 `index.html`, `app.js`, `style.css`, `src`의 JS/CSS가 모두 포함되는지 확인한다. 관리자 개발용 `scripts`, `package.json`, `.d.ts`는 배포하지 않는다.

## UI 회귀 확인 항목

- 모바일: 최초 명시적 동의, 소셜 전용 로그인, 홈 우선순위, 5개 탭, 기록·경과 사진, 문진·결과, 뒤로가기, 백과·병원·마이, 오류/빈 목록, 확대 글꼴/모션 감소.
- 관리자: 모듈 로딩 오류 없음, 로그인, 문의 열람/답변, 기관 편집, 분석/AI 상태, 검수 요청과 승인 권한, 감사 이력, 비밀번호 변경/로그아웃/인증 만료.
- 실제 API·모델과 분리한 합성 UI 자료는 화면/통신 계약 검사용이다. 의료 정확도나 실휴대전화 동작을 검증한 것으로 해석하지 않는다.

2026-11-04 폴더 정리의 실행 결과와 이동 목록은 작업 산출물 `outputs/project-structure-v1/`에 보관한다. 기존 v6 실휴대전화 확인 결과는 그 이전 작업의 결과이며, 이번 소스 변경의 APK 재설치 결과가 아니다.

## 2026-11-04 실행 결과

- 모바일 타입 검사 및 웹 export 통과, 기존 단위 테스트 46개 통과.
- 합성 모바일 UI 24항목, 관리자 UI 10항목 통과.
- 관리자 JS 18개 구문/import 검사 통과. 정적 자원 21개가 JAR에 원본과 동일하게 포함됨.
- 백엔드 94개 중 93개 통과, 조건부 실제 AI 연결 테스트 1개 미실행. `bootJar` 성공.
- 새 `AdminStaticResourcesTests`는 로그인 전 JS/CSS 접근과 MIME/내용, 관리자 API 접근 제한, 개발 파일 배포 제외를 검사한다. 새 검사 3개와 상속한 기본 컨텍스트 검사 1개가 통과했다.

화면 결과는 로컬 합성 자료로 확인했다. 이번 구조 변경에 대한 새 APK 실휴대전화 검증이나 공개 서버 재배포는 수행하지 않았다.
