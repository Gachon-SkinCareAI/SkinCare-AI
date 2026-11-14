# Public operations UI

The Spring Boot artifact serves this authenticated API-backed UI at `/admin/ops/`
(the React console in `../src` is served at `/admin/`). Keep links relative:
the page is opened only at `/admin/ops/` (with the trailing slash).
The entry point loads native JavaScript modules from `src/`; Spring Boot packages
these modules alongside `index.html`, `app.js`, and `style.css`.
Supported screens: inquiries, medical institutions, analysis and AI status,
content reviews, audit history, and administrator password changes.

The React/Vite application under `../src` is the main console at `/admin/`. It
uses the real admin API but does not yet have content reviews, audit history or
administrator password change, so this console stays available at `/admin/ops/`
(linked from the React sidebar as "운영 도구") until those screens are ported.
Do not put user passwords, patient records, or provider keys in either source tree.

## Module ownership

- `app.js`: login, logout, navigation, password change, event initialization.
- `src/api.js`: same-origin API transport, timeout, authentication failures.
- `src/state.js`: in-memory token, pagination and editor state.
- `src/ui.js`, `src/constants.js`: safe text rendering, messages, locking, labels.
- `src/dashboard.js`: dashboard summary.
- `src/inquiries.js`, `src/hospitals.js`: inquiry replies and institution editing.
- `src/operations.js`: analysis counts, AI health, configuration readiness.
- `src/reviews.js`, `src/audit.js`: versioned review workflow and audit history.

Run `npm run test:operational` in `admin-web` for a browser regression using
synthetic API responses. It checks module loading, tab transitions, a reply request,
and cleanup after session expiry. Backend tests additionally check anonymous
static asset access and USER/ADMIN API separation.
