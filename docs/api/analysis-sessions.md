# Analysis session MVP

All endpoints require an active USER JWT with current required consents. They return direct JSON (not ApiResponseDto wrappers). AI service tokens never go to the mobile client. Session IDs and image routes are scoped to the authenticated owner; another user's IDs return 404, including on mutation, image reads and parent links.

| Method/path | Request | Response |
|---|---|---|
| POST /api/wc/analyses | multipart `file`, canonical UUID `clientRequestId`, optional `parentId` | 201 + persisted session, Location header |
| GET /api/wc/analyses | zero-based `page`, `size` 1..50, optional `caseId` | newest-first summaries, without loading photo blobs |
| GET /api/wc/analyses/{id} | none | current question, slots, raw vision output, result and saved answer history |
| GET /api/wc/analyses/{id}/image | none | authenticated JPEG/PNG, Cache-Control no-store |
| POST /api/wc/analyses/{id}/answers | JSON below | updated session |
| POST /api/wc/analyses/{id}/retry | `{"expectedVersion": 1}` | restored question / initial AI retry / missing guide retry |
| DELETE /api/wc/analyses/{id} | none | 204; deletes that photo/session and its answers, not other case records |

Answer body:

```json
{"clientRequestId":"a new UUID for this logical answer","expectedVersion":1,"questionId":"common.systemic","answer":"none","userText":""}
```

The client cannot submit risk levels, vision results or accumulated slots. It must answer the question currently stored on the server. A choice must be one of that question's saved options. Free text is optional (maximum 2000 characters); a non-empty free-text response may be sent without a choice. An explicit completion confirmation has no choices and accepts a null answer. The AI server validates structured choices against its own question bank as well.

## State / retry rules

- CREATED is an internal initial state. A synchronous create normally returns QUESTIONNAIRE, COMPLETED or RETRY_REQUIRED.
- QUESTIONNAIRE contains the current question and committed answers. GET restores it without another AI call.
- COMPLETED contains level, matched rule IDs and a source-backed guide when available. It can be UNKNOWN. UNKNOWN is not a declaration of safety.
- RETRY_REQUIRED contains an AI_UNAVAILABLE code and preserves the last committed turn/question and photo. Failed answers are not added to history. Retry restores the saved question; the user submits the failed answer again. If initial prediction failed, retry calls AI again.
- An emergency rule result is stored and returned without waiting for either final analysis or guide HTTP calls. Its guide is marked `emergency_priority` and can be fetched with retry after the urgent result has been displayed.
- Missing non-emergency guide content never discards a completed risk result. Retry may load it later.
- Uploads are bounded to 8MB and decoded JPEG/PNG to 20 million pixels; metadata is stripped by re-encoding. The client normalizes EXIF orientation before upload. No public/static image URLs are created.

## Idempotency / concurrency

Use a stable create UUID when retrying an upload whose response was lost. Reusing it with a different photo or parent case conflicts. Each answer gets its own UUID; replaying the same committed answer returns current state even if the supplied version is now old. Reusing that UUID with different content conflicts. A database write lock and expectedVersion protect against two devices overwriting each other: only the first distinct answer at a version commits, the other receives 409. Reload on 409. Failed AI requests are not committed as answers and do not consume a turn.

Creation locks the owner's user row to serialize same-account duplicate creates. Network calls are synchronous within the transaction for this MVP; a future high-volume release should use durable jobs and short claim/commit transactions instead. The DB stores photo blobs; an object store with authenticated reads and lifecycle policies is a future scale improvement. API calls can take up to the configured per-hop AI timeout. List queries intentionally exclude photo blobs.

## Same-wound history

Provide an existing owned `parentId` on upload to reuse that session's `caseId`. Each date/photo remains a separate analysis. List with `caseId` for the timeline. The app does not infer healing, true area changes or infection from the timeline, and does not expose the old image-comparison ratios as clinical measurements.

## Real integration test

`LiveAnalysisFlowTests` is opt-in via AI_LIVE_TEST=true, AI_LIVE_URL and AI_SERVICE_TOKEN. Start an actual FastAPI service with trusted baseline model paths first. This test starts Spring Boot on a random loopback port with an isolated H2 database, creates a synthetic test account/photo, then exercises login, multipart upload, questions, persisted results, resume, history, emergency and deletion over real HTTP. It never uses the production database or sends mail. Normal tests use an AI mock to cover failures, authorization and concurrency deterministically.

The existing `diagnoses` schema remains for compatibility; these new APIs use `analysis_sessions` and `analysis_answers` because the old table required a completed diagnosis and could not represent an unfinished questionnaire. No production DB migration was executed automatically.
