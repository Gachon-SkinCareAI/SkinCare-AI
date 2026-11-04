# 경과 기록과 관리자 운영 상태

2026-10-31 구현. 기존 분석 세션 API와 AI 추론 계약은 유지한다.

## 경과 기록

분석 상세 응답에 `progress: { painScore: number | null, note: string }`를 추가한다.
기존 기록의 기본값은 통증 미기록(null), 메모 없음(빈 문자열)이다. 미기록을 통증 0으로 바꾸지 않는다.

| 메서드와 경로 | 내용 |
|---|---|
| PUT /api/wc/analyses/{id}/progress | `{expectedVersion, painScore, note}` 저장 후 갱신된 분석 상세 반환 |
| GET /api/wc/analyses/{id}/comparison | 현재 기록과 같은 상처의 남아 있는 가장 이른 기록 반환 |
| GET /api/wc/analyses/{id}/comparison?referenceId={id} | 같은 상처의 특정 기록과 비교 |

통증은 선택 입력인 정수 0~10이다. 소수, 범위 밖 숫자는 400이다. 메모는 앞뒤 공백을 제거하여 2000자 이하로 저장한다. 통증 null과 메모 null/빈 문자열로 기존 값을 비울 수 있다. 입력 내용으로 AI 재분석이나 위험도 변경을 수행하지 않는다.

저장과 비교 모두 활성 USER, 최신 필수 동의, 해당 기록 소유권을 요구한다. 다른 사용자의 기록은 404, 같은 사용자의 다른 상처는 비교 시 400이다. 응답과 사진은 `Cache-Control: no-store`다.

저장은 행 잠금과 기존 `expectedVersion`을 사용한다. 저장된 값과 완전히 같은 재전송은 현재 기록을 반환한다. 값이 달라진 오래된 버전 요청은 409이며, 두 개의 서로 다른 동시 수정 중 하나만 반영된다. 사진, 문진, 결과를 덮어쓰지 않는다.

비교 응답은 `{current, reference, sameRecord, recordCount}`다. 각 기록에는 ID, caseId, 처리 상태, 기록일, 본인 인증이 필요한 imagePath, progress 등이 들어간다. 문진 원문과 답변 목록은 포함하지 않는다. 기록이 하나면 sameRecord=true다. 원래 첫 사진이 삭제되면 남아 있는 가장 이른 기록을 기준으로 삼는다. 기록일은 업로드 시각이며 EXIF 촬영일을 추정하지 않는다.

앱은 비교를 좁은 화면에서는 위아래, 넓은 화면에서는 나란히 배치한다. 사진의 면적·회복률·감염 여부는 계산하지 않는다. 저장 충돌 시 입력한 메모를 화면에 보존하며, 사용자가 명시적으로 최신 기록을 불러올 때 입력을 버린다. 저장 전 탭 이동/종료의 입력 보존은 제공하지 않으며 화면에 수동 저장임을 안내한다.

## 관리자 운영 상태

| 메서드와 경로 | 내용 |
|---|---|
| GET /api/wc/admin/operations | 상태별 집계와 페이지 목록. status 선택, page 기본 0, size 기본 20 (1~50) |
| GET /api/wc/admin/operations/ai-status | 서버 측 AI 상태 조회. 클라이언트에 AI 인증 토큰을 전달하지 않음 |

ADMIN 전용이다. 상태 필터는 CREATED, QUESTIONNAIRE, COMPLETED, RETRY_REQUIRED다. 결과의 UNKNOWN은 처리 상태와 구분하며 상태 필터에 넣지 않는다. 반환 구조는 `{counts, items, page, size, hasNext, total}`다. counts는 전체 보관 기록의 상태별 개수, total은 현재 필터에 해당하는 개수다.

목록은 기록 ID, 상태, 저장된 문진 차수, 생성/수정 시각, 제한된 오류 코드(AI_UNAVAILABLE)만 포함한다. DB 쿼리도 사진 BLOB, 답변, 결과 JSON, 사용자 ID, 통증, 메모를 읽지 않는 projection을 사용한다. 관리자에게 사용자 사진 조회 권한을 새로 부여하지 않는다. 삭제된 세션은 집계에서 빠지므로 누적 처리 실적 지표로 사용하지 않는다. 완료 건수는 정확도·Coverage·임상 성능 지표가 아니다.

AI 서버의 기존 `/api/v1/ai/health`를 서버 인증으로 조회한다. 연결 제한 2초, 읽기 제한 3초이며 상태 조회가 실패해도 분석 목록은 별도로 조회할 수 있다. 상태는 READY, DEGRADED, UNAVAILABLE, AUTH_ERROR, UNCONFIGURED, INVALID_RESPONSE다. 원본 응답과 오류 본문을 중계하지 않는다. 모델 등록명, 질문/카드 버전, 선택형 LLM 설정 여부, 처리 중 요청 수만 제한하여 반환한다.

모델명은 AI 서버가 선언한 메타데이터다. 파일 해시/아키텍처 실측이나 성능 검증 결과로 취급하지 않는다. 이번 기능은 조회 전용이며 재학습 실행, 기본 모델 교체, 임계값 변경을 제공하지 않는다.

## 데이터베이스 적용

MySQL 기존 DB는 앞선 마이그레이션 이후 `docs/db/20261031_progress_records.sql`을 한 번 적용한다. 추가하는 컬럼은 nullable pain_score, progress_note 두 개다. 실행 전 대상/백업/컬럼 존재 여부를 확인한다. 이번 개발에서는 사용자 DB에 SQL을 실행하지 않았다.

테스트는 격리 H2와 로컬 HTTP AI 상태 fixture로 실행한다. 실제 MySQL, 실제 휴대폰, 공급자 OAuth/메일/푸시 검증을 대체하지 않는다.
