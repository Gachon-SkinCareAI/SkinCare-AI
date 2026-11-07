import { state } from './state.js';

export const $ = s => document.querySelector(s);

export function element(tag, text, className) { const e = document.createElement(tag); if (text !== undefined)
    e.textContent = text; if (className)
    e.className = className; return e; }

export function message(text, error = false) { const m = $('#message'); m.textContent = text; m.className = error ? 'error' : ''; m.hidden = !text; m.setAttribute('role', error ? 'alert' : 'status'); }

export function signedOut() { state.token = null; state.selectedInquiry = null; state.editingHospital = null; state.replyRequest = null; $('#workspace').hidden = true; $('#login-panel').hidden = false; $('#logout').hidden = true; for (const id of ['inquiry-list', 'inquiry-detail', 'hospital-list', 'dashboard', 'analysis-counts', 'analysis-list', 'analysis-pages', 'ai-status', 'review-list', 'review-detail', 'review-pages', 'audit-list', 'audit-pages', 'review-capabilities', 'readiness-status'])
    $('#' + id).replaceChildren(); $('#login-form').reset(); $('#review-form').reset(); $('#review-editor').hidden = true; state.reviewRequest = null; state.reviewCapabilities = null; }

export function lock(value) { state.busy = value; $('#progress').hidden = !value; document.querySelectorAll('button,input,select,textarea').forEach(e => { e.disabled = value || e.dataset.permanentDisabled === 'true'; }); }

export async function run(task) { if (state.busy)
    return; lock(true); message(''); try {
    await task();
}
catch (e) {
    message(e.message || '연결 상태를 확인해 주세요.', true);
}
finally {
    lock(false);
} }

export function showTab(next) { state.tab = next; for (const name of ['inquiries', 'hospitals', 'operations', 'reviews', 'audit', 'account'])
    $('#' + name + '-panel').hidden = next !== name; document.querySelectorAll('[data-tab]').forEach(e => { e.className = e.dataset.tab === next ? '' : 'secondary'; e.setAttribute('aria-current', e.dataset.tab === next ? 'page' : 'false'); }); }

export function pages(root, result, load) { root.replaceChildren(); root.append(element('span', `총 ${result.total}건 · ${result.page + 1}페이지`, 'muted')); if (result.page > 0) {
    const b = element('button', '이전', 'secondary');
    b.onclick = () => run(() => load(result.page - 1));
    root.append(b);
} if (result.hasNext) {
    const b = element('button', '다음', 'secondary');
    b.onclick = () => run(() => load(result.page + 1));
    root.append(b);
} }
