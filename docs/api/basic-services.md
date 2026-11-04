# Basic services

The analysis API and default AI weights are unchanged. These services use the existing USER/ADMIN JWT roles. Private responses use `Cache-Control: no-store`.

## Account

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/wc/users/me` | Own profile, provider and recorded policy versions; no token/password |
| PUT | `/api/wc/users/me` | Existing profile API; nickname validated, optional image URL must use HTTPS |
| GET/PUT | `/api/wc/users/me/settings` | Persist notification preferences; missing legacy settings row is created on update |
| PUT | `/api/wc/users/me/password` | 410 Gone: user authentication is social-only; use the provider account settings |

Push preferences do not imply push delivery; no delivery provider/device-token integration was added. The existing withdrawal API remains a soft account withdrawal; no claim of complete data erasure was added to the UI.

## Support inquiries

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/wc/inquiries` | `{clientRequestId: UUID,type,title,content,analysisId?}` |
| GET | `/api/wc/inquiries?page=0&size=20` | Own summaries only |
| GET | `/api/wc/inquiries/{id}` | Own content and answer; other owners receive 404 |
| GET | `/api/wc/admin/inquiries?status=WAITING` | Admin queue; omit status for all |
| GET | `/api/wc/admin/inquiries/{id}` | Admin detail; does not expose the linked photo or questionnaire |
| PUT | `/api/wc/admin/inquiries/{id}/answer` | `{clientRequestId: UUID,expectedVersion,content}` |

Types: AI_RESULT, ACCURACY, SYSTEM_ERROR, OTHER. Title 1–200 chars; content/answer 1–5000. The server validates ownership of an optional analysis reference. That ID is a snapshot: deleting an analysis does not erase the support request. Support messages may contain sensitive text; access is owner/admin only. They are not automatically included in model training.

Repeated identical creation requests with the same user and UUID return the same inquiry. Different content under the same UUID returns 409. Answers are immutable after submission; the same administrator can replay the same answer request. A different answer or stale version returns 409. Row locking and a unique inquiry-answer relationship prevent concurrent overwrites. User self-service inquiry deletion and threaded replies are outside this increment.

## Hospital directory

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/wc/hospitals?q=...&type=...&emergency=true&page=0&size=20` | Active records with source URL and verification date |
| GET | `/api/wc/admin/hospitals?q=...` | Includes hidden/unreviewed records |
| POST | `/api/wc/admin/hospitals` | Create from explicitly entered, checked source information |
| PUT | `/api/wc/admin/hospitals/{id}` | Update or hide using expectedVersion; stale updates return 409 |

Fields: name, type (DERMATOLOGY/GENERAL_HOSPITAL/PHARMACY/OTHER), region, address, tel?, nightCare, emergency, dataSource, sourceUrl (HTTPS), verifiedOn (YYYY-MM-DD, not future in Asia/Seoul), status (ACTIVE/INACTIVE). POST also requires clientRequestId UUID; identical initial retries return the original record, changed payloads conflict. PUT requires expectedVersion. No automatic import or fabricated hospital seed data is included. A recorded verification date is an administrator claim, not proof of official approval.

Night-care/emergency flags are directory information, never live opening/capacity indicators. Map links use [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started) with an encoded name/address; they do not require a Places lookup or upload a user's analysis image. External maps may resolve a query imprecisely. Users confirm the institution and availability before visiting.

All paged services return `{items,page,size,hasNext,total}`; size 1–50, page 0–10000. Search text is bounded and LIKE wildcard characters are escaped.

## Admin console and setup

The static console in `admin-web/` is packaged into the Spring Boot JAR at `/admin/`. It uses same-origin APIs, an in-memory token and plain text rendering for user messages. `GET /api/wc/admin/dashboard` returns inquiry/directory counts only. All admin APIs remain protected even though the login HTML/JS/CSS are public.

For the first administrator in an empty admin table, set `SKINCARE_BOOTSTRAP_ADMIN_ID` and `SKINCARE_BOOTSTRAP_ADMIN_PASSWORD` for one startup. Use a unique password, then remove those bootstrap environment variables. Existing admin accounts are never overwritten. No public admin signup endpoint is opened and no default production password is supplied. Existing admins may use the pre-existing authenticated admin signup API to add administrators.

Existing MySQL databases require `docs/db/20261030_basic_services.sql`, after the earlier migrations. Review duplicate answers and existing unique indexes first. This work does not apply migrations to an existing database. Tests use isolated H2; actual MySQL deployment and provider-backed push, email and OAuth remain separate validation work.

## Checks

Backend: `gradlew.bat test bootJar` (JDK 17 toolchain). Mobile: `npm run typecheck`, `npm test`, `npm run export:web`. `BasicServiceFlowTests` covers ownership, inquiry replay, concurrent answers, stale institution edits, source validation, profile/settings persistence and password token revocation. Browser QA uses an isolated H2 database with explicitly synthetic accounts/institutions, not medical evaluation data.

User sign-in and registration use Google/Kakao/Naver. See [social authentication](../social-auth.md). Legacy local credential endpoints remain addressable but return 410 without sending email or changing credentials.


2026-11-01 추가: 공공 의료기관 검색, 건강정보 별도 동의, 선택 학습 동의·철회, DB 기록 삭제 탈퇴, 관리자 감사·검수는 [추가 명세](service-completion.md)를 따른다. 기존 등록 기관·문의 API는 유지된다.
