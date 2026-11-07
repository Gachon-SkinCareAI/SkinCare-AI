# Public operations UI

The Spring Boot artifact serves this authenticated API-backed UI at `/admin/`.
The entry point loads native JavaScript modules from `src/`; Spring Boot packages
these modules alongside `index.html`, `app.js`, and `style.css`.
Supported screens: inquiries, medical institutions, analysis and AI status,
content reviews, audit history, and administrator password changes.

The React/Vite application under `../src` is a separate design preview using
sample data in localStorage. It is preserved but is not deployed as the operations
console until its administrator authentication and API integration are complete.
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
