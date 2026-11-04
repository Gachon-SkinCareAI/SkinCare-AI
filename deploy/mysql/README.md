# Windows 로컬 MySQL 개발 환경

기존 MySQL 서비스와 H2 기록을 유지하면서 별도 데이터 디렉터리와 루프백 포트에 MySQL을 실행한다. 실행 파일은 설치된 MySQL 8.0을 사용한다. 이 도구는 Windows 로컬 개발용이며 운영 배포나 기존 DB 자동 업그레이드 도구가 아니다.

## 초기 구성

설치된 MySQL, Java, Python AI 가상환경과 기존 모델 경로를 준비한다. 모든 명령은 저장소 루트에서 실행한다.

```powershell
python -B deploy/mysql/manage.py init --runtime C:/private/skincare-mysql --mysql-home "C:/Program Files/MySQL/MySQL Server 8.0" --port 13306
python -B deploy/mysql/manage.py migrate --runtime C:/private/skincare-mysql --database skincare_dev
```

init는 비어 있는 새 폴더만 허용한다. 현재 Windows 사용자와 SYSTEM에만 런타임 접근 권한을 부여한다. 초기화 동안 네트워크 서버는 실행하지 않으며 첫 서버 시작 시 생성한 루트 비밀번호와 앱 계정을 설정한 뒤 초기 설정 파일을 제거한다. 비밀번호는 명령행/출력에 넣지 않고 private 아래 옵션 파일로 전달한다. 포트는 127.0.0.1에만 바인딩하고 MySQL X 프로토콜은 끈다. 기존 시스템 my.ini와 로그인 경로 파일을 사용하지 않는다.

`config.example.json`을 private/app-config.json으로 복사하여 경로를 설정한다. runtime은 init에 사용한 폴더와 일치해야 한다. 모바일 웹 빌드는 해당 api_port를 사용해야 하며 기본은 9800이다.

```powershell
python -B deploy/mysql/run_app.py --config C:/private/skincare-mysql/private/app-config.json
```

사용자는 구글·카카오·네이버 소셜 로그인으로만 이용한다. 시험 계정을 만들지 않으며 기존 계정/기록은 유지한다. `social-config.example.json`을 private/social-config.json에 복사하고 실제 제공자 키를 입력하면 해당 로그인 버튼이 활성화된다. [소셜 설정 안내](../../docs/social-auth.md)를 참고한다. 최초 관리자는 SKINCARE_BOOTSTRAP_ADMIN_ID/PASSWORD를 명시하여 시작할 때만 생성하며 기존 관리자는 유지한다.

run_app은 실제 AI 모델, Spring, 웹 프리뷰와 자신이 시작한 MySQL의 수명을 관리한다. app/stop.request 파일을 만들면 정상 종료한다. 별도로 실행한 MySQL은 앱 종료 시 중단하지 않는다. 기존 서비스가 포트를 사용하면 해당 프로세스를 종료하지 않고 시작을 거부한다. H2 데이터를 복사하거나 삭제하지 않는다.

## 스키마와 권한

- `backend/src/main/resources/db/mysql/V001__baseline_20261031.sql`: 현재 코드의 신규 전체 스키마.
- `V002__analysis_integrity.sql`: 분석 소유자/답변 FK, 통증 CHECK, 조회 인덱스.
- schema_migrations에 SHA-256, STARTED/APPLIED 상태와 적용 시각을 보존한다.
- 같은 파일 재실행은 변경 없음. 체크섬이 달라지거나 이전 실행이 일부만 완료되면 중단한다.
- 테이블이 이미 있는 미관리 DB에는 baseline을 자동 적용하지 않는다. 기존 docs/db의 추가 마이그레이션과 해당 DB 구조를 별도로 검토해야 한다.
- mysql 프로필은 `ddl-auto=validate`다. 서버 시작 시 자동 CREATE/ALTER를 수행하지 않는다.
- 앱용 skincare_app은 지정된 DB의 SELECT/INSERT/UPDATE/DELETE만 가진다. 마이그레이션/백업/복구는 루트 관리 경로에서 분리 실행한다.
- JDBC와 CLI의 로컬 접속은 TLS를 요구한다. 로컬 자동 생성 인증서를 쓰므로 운영용 서버 신원 검증 설정을 대체하지 않는다.

## 백업과 복구

```powershell
python -B deploy/mysql/manage.py backup --runtime C:/private/skincare-mysql --database skincare_dev
python -B deploy/mysql/manage.py restore --runtime C:/private/skincare-mysql --backup C:/private/skincare-mysql/private/backups/<backup>.sql --database skincare_restore_review
```

백업은 InnoDB 스냅샷과 hex-blob을 사용하여 사진 바이너리를 보존한다. 마이그레이션과 같은 로컬 관리 잠금을 사용한다. 외부 SQL 도구에서 백업 중 DDL을 실행하면 안 된다. SQL 파일과 체크섬 manifest는 private/backups에만 저장한다. 사진/문진/계정 해시가 포함되므로 공개 저장소나 보고서에 첨부하지 않는다.

복구는 체크섬을 확인하고 존재하지 않는 skincare_restore_ 접두사의 새 DB에만 수행한다. 기존 원본 DB를 덮어쓰거나 삭제하지 않는다. 복구 후 앱의 DB_URL을 복구 DB로 지정하여 validate 시작과 실제 조회를 확인하고 전환 여부를 별도로 결정한다. 이름만 바꾼 원본 서버 연결로 복구 성공을 판단하지 않는다.

## 검증 범위

2026-10-31에 MySQL 8.0.46과 실제 기존 AI 서버로 사진/문진/결과, 경과, 문의/답변, 설정, 등록 기관을 확인했다. MySQL+Spring 재시작과 새 DB 백업 복구 후 응답 내용과 이미지 SHA-256을 비교한다. 검증 결과와 실행 도구는 작업 outputs/mysql-development-v1에 있다. 합성 시험 자료는 별도 skincare_qa_ DB에만 저장하며 개발용 skincare_dev에 복사하지 않는다.

공식 동작 근거:
- MySQL 데이터 디렉터리 초기화: https://dev.mysql.com/doc/refman/8.0/en/data-directory-initialization.html
- mysqldump 일관성·바이너리 백업: https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html


## 2026-11-01 서비스 확장

V003 소셜 계정과 V004 목적별 동의·감사·검수·탈퇴 목록을 순서대로 적용한다. `run_app.py`는 신규 버전이 있으면 먼저 기존 개발 DB를 백업한다. 마이그레이션은 기존 계정·사진·라벨을 삭제하지 않는다. 건강정보 별도 동의가 없는 계정은 업데이트된 앱에서 재동의한다.

`service-config.example.json`을 **같은 런타임의 `private/service-config.json`**에 복사한다. 값이 없으면 빈 문자열로 유지한다.

- `PUBLIC_MEDICAL_SERVICE_KEY`: 공공데이터포털의 디코딩된 서비스키. 병·의원/약국 서비스 모두 활용신청 후 검증한다. 공개 EXPO_PUBLIC 값에 넣지 않는다.
- `TRAINING_POLICY_URL`: 검토가 끝난 선택 학습 목적 안내 문서의 HTTPS URL. 없으면 신규 동의 비활성, 철회 가능. 학습 반출은 구현하지 않았다.
- `SKINCARE_CLINICAL_REVIEWERS`: 확인된 임상 검수 담당자의 ADMIN ID를 쉼표로 구분한다. 빈 값은 누구도 임상 승인할 수 없다. 작성자는 자신이 등록한 문서를 승인할 수 없다.

복구 도구는 백업 출처 DB의 최신 탈퇴 목록과 private/erasure-ledger.json을 합쳐, 복원된 계정·건강자료를 다시 삭제한 뒤 앱 권한을 부여한다. 기존 DB를 지우지 않고 새 복원 DB에서만 처리한다. 현재 원본 DB를 읽을 수 없으면 자동 복원을 차단한다. SQL 파일을 수동 import하면 이 절차가 실행되지 않으므로 앱 연결 전에 별도 삭제 목록 검증이 필요하다. ledger는 공개 산출물에 포함하지 말고 백업과 독립적으로 보호한다. 백업과 감사 이력의 운영 보관·자동 만료 정책은 별도 확정 사항이다.

사진은 현행 분석 세션의 DB BLOB이다. 탈퇴로 해당 BLOB·문진·경과·문의·계정을 삭제하지만 과거 레거시 URL의 외부 이미지 파일 소유권을 가정하지 않는다. 기존 소프트 탈퇴 계정을 일괄 정리하지 않는다.

모바일 네이티브 위치 모듈을 추가했으므로 다음 실제 Android 빌드에서는 `expo prebuild`/development build 갱신이 필요하다. 현재 웹 미리보기는 native 패키지를 대신한 실기기 검증이 아니다.

복원 검증과 운영 전환은 구분한다. 운영 전환 직전에는 원본 앱의 사용자 쓰기를 중지하고 최신 탈퇴 목록을 다시 반영해야 한다. 복원 후 원본 DB에 새 탈퇴가 발생하면 기존 복원본에 자동 전파되지 않는다. 현재 도구는 자동 운영 전환을 하지 않는다.


## 정책 설정·준비 상태 점검 (2026-11-01 후속)

이용약관과 개인정보 문서는 서버의 `TERMS_POLICY_URL`, `PRIVACY_POLICY_URL`에 설정한다. private/service-config.json에서 기존 키를 유지하며 두 항목을 추가한다. 앱은 서버에서 정책 버전과 공개 URL을 받는다. 둘 중 하나라도 없거나 잘못되면 새 동의 저장은 서버에서도 409로 차단한다. 기존 앱의 EXPO_PUBLIC 정책 URL 설정만으로는 활성화되지 않는다.

`python -B deploy/mysql/check_setup.py --config <private/app-config.json>`으로 비밀값을 노출하지 않고 로컬 설정을 검사한다. 코드는 0 또는 2를 반환하며 2는 아직 필요한 설정이 있다는 뜻이다. 파일/설정만 읽으며 서버·DB를 변경하거나 실제 외부 제공자에게 요청하지 않는다. 관리자 웹의 분석·AI 상태에도 동일 목적의 설정 현황이 추가됐다. [상세](../../docs/api/service-readiness.md).


## 2026-11-01 보관 정책 연결

`SKINCARE_BUILTIN_POLICIES=true`는 버전 2026-11-01.2의 운영자·보관 정책을 연결한다. 기본값 false. 관리 실행기는 V005 적용 후 보관기한 점검을 먼저 실행하고 실행 중에도 약 1분 주기로 반복한다. 직접 Spring만 실행하는 구성에는 자동 점검이 없다.

`manage.py retention-check --runtime <path>`는 읽기 전용 대상 확인, `retention`은 실제 만료·백업 교체다. 삭제 목록/기한을 복원 전에 적용하고, 삭제 또는 기한 만료가 반영된 백업을 만든 다음 해당 DB의 과거 백업 파일을 제거한다. 타 DB 백업, 학습 데이터 및 기본 모델은 대상이 아니다. 손상·미완성 백업은 자동 파기하지 않고 오류로 남긴다. 자세한 범위·주의·명령은 [문서 연결 명세](../../docs/api/policy-documents.md)를 참조한다.
