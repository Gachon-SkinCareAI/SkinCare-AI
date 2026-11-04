# 이용 문서 연결 및 보관 정책 (2026-11-01.2)

운영 주체 장재완, 문의 wanjang0320@gachon.ac.kr. 사용자가 확인한 기준은 수집일부터 최대 5년, 삭제·탈퇴 요청 시 조기 삭제, 관리 백업에도 삭제 반영이다. 5년을 법정 의무로 표시하지 않는다. 의료 기능은 연구·시연 범위이고 임상/법률 검토 완료를 뜻하지 않는다.

## 실제 문서와 API

- GET `/api/wc/policies/2026-11-01.2/terms`, `/api/wc/policies/2026-11-01.2/privacy`: 로그인 없이 읽는 버전 고정 HTML. 개인정보의 `#health-data`는 별도 건강정보 안내다.
- `/api/wc/auth/consent-policy` 기존 계약을 유지한다. TERMS/PRIVACY/HEALTH_DATA 버전은 `2026-11-01.2`다. 과거 동의를 일괄 변경하지 않으며 다음 로그인·접근 시 직접 재동의해야 한다.
- 기존 `policy-previews` 초안은 보존한다. 초안 URL은 여전히 가입 동의 문서로 사용할 수 없다.
- HTML은 no-store, noindex, no-referrer, 외부 스크립트·폼 차단 CSP로 제공한다. 문서 열람 자체는 동의를 기록하지 않는다.

## 설정

관리 실행기 `deploy/mysql/run_app.py`의 비공개 `private/service-config.json`에 `"SKINCARE_BUILTIN_POLICIES": "true"`를 두면 서버 내장 문서를 사용한다. 기본값은 false다. 주소는 검증된 `SOCIAL_PUBLIC_BASE_URL`에서 만든다. HTTPS가 기본이며 명시적 로컬 모드의 localhost/127.0.0.1/[::1] HTTP만 예외다. 모바일도 해당 버전의 고정 경로만 허용하고 API origin이 같은지 확인한다.

외부 문서를 사용할 때는 내장 모드를 끄고 `TERMS_POLICY_URL`, `PRIVACY_POLICY_URL`에 실제 HTTPS URL을 지정한다. 선택적 학습 문서·반출은 이번 변경으로 활성화하지 않는다. 공개 배포 시 주소·데이터 처리 업체·실제 백업 범위 등을 다시 검토해야 한다.

## 삭제·만료·복원

- V005는 `analysis_erasure_receipts`만 추가한다. 이전 마이그레이션과 현재 사용자 자료는 변경하지 않는다.
- 개별 분석 삭제 시 문진 응답·사진과 복원 방지 식별자를 같은 트랜잭션으로 처리한다. 탈퇴는 기존 계정 삭제 트랜잭션을 유지한다.
- 관리 실행기는 서비스 시작 전과 실행 중 약 1분 간격으로 보관기한을 점검한다. 중지 중 만료된 자료는 다음 시작 시 처리한다. 유지보수 실패 시 서비스를 종료하고 private app 상태에 오류를 남긴다.
- 수집시각 + 달력 기준 5년을 기준으로 계정·분석·문의·동의·설정·감사·삭제 목록 등을 제거한다. 계정은 생성일 기준이고 갱신·접속으로 기한을 연장하지 않는다. 만료 계정에 연결된 기록은 함께 삭제한다. 윤년 날짜는 MySQL DATE_ADD의 달력 연산을 사용한다.
- 삭제 감지 또는 백업에 담긴 자료의 만료 시 새 백업을 성공적으로 만든 뒤 이전 관리 백업을 제거한다. 경로·DB 식별자·체크섬이 맞지 않으면 임의 삭제하지 않고 오류를 반환한다. 다른 DB의 백업은 건드리지 않는다.
- 과거 백업 복원은 새 `skincare_restore_*` DB만 허용한다. 최신 원본 DB의 계정/기록 삭제 목록과 만료 기준을 적용한 뒤 앱 권한을 부여한다. 원본 DB가 없으면 오래된 파일만으로 복원을 허용하지 않는다.
- 관리 도구 밖으로 복사한 파일, OS/클라우드 사본, 저장장치 물리적 완전 소거는 자동화 범위가 아니다. 이 사본은 운영자가 별도로 삭제·검증해야 한다. 직접 Spring만 실행하면 이 관리 작업이 수행되지 않으므로 현재 정책은 관리 실행기 환경에서 사용한다.

명령 예시 (먼저 관리 인스턴스를 실행):
```powershell
python -B deploy/mysql/manage.py retention-check --runtime <instance-path> --database skincare_dev
python -B deploy/mysql/manage.py retention --runtime <instance-path> --database skincare_dev
```
첫 명령은 대상 건수만 확인한다. 두 번째 명령은 실제 만료·백업 교체를 수행한다. 기존 학습 데이터·라벨·AI 모델에는 접근하지 않는다.

## 검증 범위

Spring 테스트: 문서 익명 접근, 현재 버전 동의, 과거 동의 재확인, 삭제 식별자. 별도 MySQL DB: 탈퇴/개별 삭제/기간 만료의 백업 복원 차단, 원본 보존, 타 DB 백업 보존, 체크섬 오류 처리. 브라우저: 로그인 전 링크, 별도 동의 항목과 문서 버전 전달. UI 테스트의 인증 응답은 테스트 fixture이며 실제 제공자 로그인을 검증했다는 뜻이 아니다.

운영자 설정에 따른 최대 보관기간이며 무조건 5년을 채워 보관한다는 의미가 아니다. [개인정보 보호법 제21조](https://law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0021&lsiSeq=283839&urlMode=lsScJoRltInfoR), [제15조](https://law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1020398563)를 참고했다.
