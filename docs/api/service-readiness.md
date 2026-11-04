# 정책 연결과 서비스 준비 상태

2026-11-01 후속 보완. 외부 키나 전문가 승인을 대신 만들지 않고, 서버 설정과 실제 검증 여부를 구분한다.

## 서버 기준 정책

공개 `GET /api/wc/auth/consent-policy`는 no-store 응답이다. requiredAvailable, terms/ privacy/ healthData의 version·url, training의 version·url, trainingAvailable, trainingExportEnabled=false를 반환한다. 공개 문서 URL만 포함하며 키·관리자 ID·로컬 경로는 포함하지 않는다.

서버의 `TERMS_POLICY_URL`, `PRIVACY_POLICY_URL`에 공개 HTTPS 문서를 연결한다. 둘 중 하나라도 없거나 주소가 잘못되면 `requiredAvailable=false`이며, `PUT /users/me/consents`도 409로 거부한다. 앱 버튼만 막던 이전 구현의 직접 API 우회를 차단했다. HTTPS URL의 호스트·포트·사용자정보를 검사하며, 연결 가능 여부와 문서 검토 상태까지 자동 확인한 것은 아니다.

앱은 더 이상 EXPO_PUBLIC_TERMS_URL/EXPO_PUBLIC_PRIVACY_URL 또는 앱에 고정된 버전으로 동의하지 않는다. 서버에서 받은 현재 문서와 버전을 표시하고 그 버전으로 제출한다. 문서 조회 오류 시 동의할 수 없고 사용자가 재조회할 수 있다. 재조회와 서버의 400/409 거부 시 체크박스를 초기화한다. 동의하지 않아도 로그아웃·탈퇴가 가능하다. 이미 유효하게 동의한 계정의 기록 접근을 정책 URL 누락만으로 소급 차단하지는 않는다.

정책 내용이 바뀌면 `ConsentPolicy`의 해당 버전과 공개 문서를 함께 변경한다. 기존 동의를 true로 일괄 설정하지 않는다. URL을 넣는 행위가 정책의 법적 승인이나 임상 검수 완료를 뜻하지 않는다. 실제 승인된 문서가 없으면 빈 값을 유지한다.

## 관리자 준비 상태

ADMIN 전용 `GET /api/wc/admin/operations/readiness`를 관리자 웹의 **분석·AI 상태 → 서비스 연결 준비 상태**에 연결했다.

- 소셜 로그인: 등록 설정 유무·일부 설정 누락.
- 이용약관/개인정보/선택 학습 안내: 누락·주소 오류·연결 설정.
- 공공 의료기관 API: 키의 설정 유무만 확인.
- 임상 검수 담당자: 지정 여부와 해당 관리자 계정 존재 여부 확인.
- 학습 반출: 비활성. 임상 서비스 승인: 검토 대기.

응답에는 키 값, 정책 URL, 검수자 ID를 넣지 않는다. CONFIGURED는 설정 확인이며 실환경 인증 성공이 아니다. externalConnectionsVerified=false와 releaseApproved=false를 유지한다. 담당자의 실제 자격이나 문서의 승인 여부를 임의 판정하지 않는다.

## 읽기 전용 로컬 점검

```powershell
python -B deploy/mysql/check_setup.py --config <private/app-config.json>
```

파일 존재와 설정 형식만 검사하고 네트워크 호출·DB 변경·파일 초기화를 하지 않는다. JSON 결과만 출력하며 비밀값은 출력하지 않는다. 반환 코드 0은 점검 대상 필수 설정이 채워졌다는 뜻이고, 2는 누락 또는 오류가 남았다는 뜻이다. 어느 경우도 서비스 출시 승인이나 실제 외부 연결 성공을 의미하지 않는다. 실제 실행 환경에 별도로 주입한 환경변수는 이 파일 점검 도구의 대상이 아니다.

`service-config.example.json`의 TERMS_POLICY_URL/PRIVACY_POLICY_URL을 해당 런타임의 private/service-config.json에 추가한다. 기존 값은 보존하고 필요한 빈 항목만 추가한다. 실제 문서가 없을 때 example.test 같은 시험 URL을 운영 설정에 입력하지 않는다.

이번 단계에는 DB 스키마 변경이 없다. 기존 모델·데이터·라벨·소셜 전용 계정 정책과 사용자 앱/관리자 웹 분리를 유지한다. 외부 키 발급, 실제 계정 인증, 기기 검증과 임상 검수는 별도 준비 사항이다.


## 권한 오류 응답

실제 내장 서버에서 일반 사용자의 관리자 API 접근이 차단된 뒤 오류 페이지 재처리를 거쳐 403 대신 401로 반환되는 문제를 수정했다. 인증이 없으면 401, 로그인했지만 필요한 권한이 없으면 403 JSON을 직접 반환한다. 앱이 단순 권한 부족을 로그인 만료로 오해하지 않도록 한다. MockMvc 외에 실제 Spring HTTP 서버에 USER 토큰으로 요청해 확인한다.

후속 문서 열람 연결: [이용 문서 초안 명세](policy-documents.md). 공개 문서가 없어도 초안을 읽을 수 있지만 동의 차단은 유지한다.
