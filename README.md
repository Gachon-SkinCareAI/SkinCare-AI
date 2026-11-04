# SkinCare-AI
Gachon University graduate project

## 현재 소스 구조 (2026-11-04)

사용자 앱은 `mobile/`, 관리자 웹은 `admin-web/`, Spring Boot는 `backend/`, FastAPI와 AI 파이프라인은 `ai/`에 둡니다. 모바일 화면과 탐색, 관리자 페이지와 API 모듈을 실제 파일로 분리했습니다.

[폴더 구조·이동 내역](docs/diagrams/project-structure.md) · [검증 명령](docs/test/README.md) · [모바일](mobile/README.md) · [관리자 웹](admin-web/README.md) · [AI](ai/README.md)

기존 API, 소셜 로그인, 기본 모델과 데이터는 유지합니다. 아래 날짜별 항목은 당시의 변경 이력으로, 이전 시점의 미완료 항목과 모델 평가 수치를 현재 검증 결과로 해석하지 않습니다.



## 2026-10-29 security and model update

- Newly reported emergency symptoms override prior non-emergency answers. Incomplete/unknown questionnaires cannot default to observation; LLM finish requests cannot skip pending safety questions.
- Image decoding uses bounded in-memory reads; request IDs are generated internally and never used as upload paths.
- JWT contents are not logged. Logout and withdrawal invalidate existing user tokens; administrator password changes invalidate administrator tokens via persisted account token versions. Inactive accounts cannot authenticate.
- Required consent is explicit and versioned. New OAuth accounts have onboarding-only access until they accept required policies. Automatic email-based linking to local accounts is rejected.

### Backend compatibility / migration

Use JDK 17 for the configured Java toolchain. The Gradle launcher must satisfy the checked-in wrapper's requirements. Run `backend/gradlew.bat -p backend test bootJar` from Windows (or `./gradlew test bootJar` inside backend on Unix).

Set `JWT_SECRET` to a unique random secret of at least 32 UTF-8 bytes; the insecure built-in fallback was removed. Apply `docs/db/20261029_security_columns.sql` **once** for an existing MySQL database, or let the entities create a fresh development database. The SQL was not applied to any existing database during this work. Existing JWTs intentionally stop working and users must log in again.

Signup now requires `agreedTerms`, `agreedPrivacy`, `termsVersion`, `privacyVersion`. The required version is `2026-10-29`. Existing and OAuth users without a recorded version receive onboarding-only access. Local login responses include `consentRequired`; the OAuth callback carries `token` and `consentRequired` in the URL fragment instead of query parameters. The client must read `location.hash` and clear it after storing the token.

Authenticated onboarding users may call `PUT /api/wc/users/me/consents` and `POST /api/wc/auth/logout`. Send the following consent body **only after presenting the actual policy documents and receiving the user's affirmative action**:

```json
{"agreedTerms": true, "agreedPrivacy": true, "termsVersion": "2026-10-29", "privacyVersion": "2026-10-29"}
```

The stored version/time fields cover required service policies; optional training-data consent is not granted by this API. The connected mobile frontend presents explicit consent controls and requires configured policy-document links. New sessions see updated consent permissions immediately. Logout invalidates all tokens for the account, not just the calling device. Tests use an isolated H2 database and fake OAuth/mail settings, not the production database or external providers.

### AI tests / reproducible classifier experiments

From `ai/`, use an environment containing `requirements.txt`, pytest and httpx:

```powershell
python -m pytest tests -q
python audit_dataset.py --data <locked-dataset> --out runs/audit.json
python train_classify.py --data <locked-dataset> --resume <trusted-baseline.pt> --out runs/new-candidate --epochs 6 --patience 3 --batch 8 --balance sampler --label-smoothing 0.05 --backbone-lr 0.000002 --head-lr 0.00002
python compare_models.py --baseline <baseline.pt> --candidate <candidate.pt> --data <locked-dataset> --out runs/comparison.json
python eval_pipeline.py --baseline <baseline.pt> --candidate <candidate.pt> --detector <detector.pt> --data <locked-dataset> --out runs/pipeline-comparison.json
```

Training now audits cross-split pixel duplicates and conflicting labels, accepts PNG/JPEG/JPG and canonical class folder aliases, uses one imbalance correction at a time, and records run configuration/audit/history. Class order is checked when loading classifier checkpoints. Exact duplicate checks cannot prove patient/source independence or exclude transformed near-duplicates.

The candidate under `ai/runs/candidate_20261029/best.pt` was fine-tuned on an RTX 3060. It is **not promoted to serving**. Validation Macro F1 increased from 0.95799 to 0.96586; fixed test Macro F1 increased from 0.93058 to 0.93583 (177 test images), but bruise recall declined from 26/30 to 25/30. The paired bootstrap 95% interval for the test F1 difference includes zero. Full vision pipeline coverage declined from 39/177 to 37/177, with correct responses declining from 38/177 to 36/177. Both pipelines returned UNKNOWN for all 84 normal-skin images. Each pipeline used a separately calibrated validation-energy cutoff; this is not an OOD benchmark.

Do not claim the classifier-only score as application or clinical accuracy. A stronger next experiment needs an independently sourced evaluation set, detector/quality analysis, a reviewed no-detection/normal-skin flow and validation-only calibration before considering deployment. Confidence thresholds were not weakened to inflate coverage.

The dataset and baseline weights were found outside this checkout under the user's existing graduation-project directory; they were read without modification. No dataset or model weights are committed. At the time of that model experiment, the frontend and Spring-to-AI session implementation had not yet been added. See the connected MVP update below for current status.

Methods reference: [PyTorch CrossEntropyLoss](https://docs.pytorch.org/docs/stable/generated/torch.nn.CrossEntropyLoss.html), [PyTorch transfer learning tutorial](https://docs.pytorch.org/tutorials/beginner/transfer_learning_tutorial.html).


## Connected analysis MVP

The mobile client and Spring-to-AI analysis session flow are now implemented. See `mobile/README.md` for launch instructions and `docs/api/analysis-sessions.md` for the API/state contract. Existing MySQL databases need the new tables in `docs/db/20261029_analysis_sessions.sql` (apply once; this task did not apply it to an existing database). The existing authentication migration is still required if not already applied.

The app uses authenticated server-owned sessions: upload a photo, answer the saved question, persist results/guides, resume from history, group follow-up photos by case, and delete a record. Duplicate requests and stale versions are handled explicitly, and all record/image access is owner-scoped. Emergency outcomes are returned before guide retrieval. Native camera/device tests and real provider/policy configuration are still required before release.

## Account, support and directory services (2026-10-30)

The mobile app now includes account/profile and notification-preference screens, social-only Google/Kakao/Naver authentication, owner-scoped support inquiries with administrator replies, and a hospital/pharmacy directory. The server also serves the administrator console at `/admin/` for the inquiry queue and institution management. The analysis session API and default models are preserved; no retraining is performed.

See `docs/api/basic-services.md` for contracts, setup and limits. Existing MySQL installations need the additive `docs/db/20261030_basic_services.sql` migration after review. Directory records must be entered with source URL and verification date; no real institution data is fabricated or preloaded. Push preferences are saved but delivery is not connected. Configure the first admin through the documented one-time bootstrap environment variables.

## Progress records and operations (2026-10-31)

The app now saves optional self-reported pain and notes per photo, compares records from the same case, and preserves drafts on stale-write conflicts. The admin console shows processing-state counts, metadata-only record lists, and an independently queried AI health status. No training or serving-model change is performed.

See `docs/api/progress-operations.md` for the additive API/database contract and `docs/implementation-status-20261031.md` for remaining scope. Existing MySQL deployments need the reviewed one-time `20261031_progress_records.sql` migration. Current medical rules/guides remain research/demo functionality pending expert review.

## Local MySQL development (2026-10-31)

`deploy/mysql/` provides a separate Windows MySQL instance, versioned schema migrations with checksums, a CRUD-only application account, and a local app launcher using the `mysql` profile (`ddl-auto=validate`). Backup/restore creates a separate restored database and preserves the original. See `deploy/mysql/README.md`; this does not migrate or delete existing H2 records or auto-upgrade an unknown populated MySQL database.

## Social-only user authentication

Google, Kakao and Naver sign-in are implemented. New social identities require explicit policy consent. Local password login/signup/email verification/reset APIs return 410; legacy account rows and records are preserved, with no automatic email-based linking. Admin login remains separate. Providers without configured keys are disabled. See [setup and verification](docs/social-auth.md).


서비스 구현 최신 상태: [2026-11-01 구현 현황](docs/implementation-status-20261101.md), [의료기관·동의·탈퇴·관리자 검수 API](docs/api/service-completion.md). 실제 공공 API 키·소셜 키·승인된 정책 문서·임상 검수 담당자는 별도 준비 사항이다.
