import { request } from './src/api.js';
import { auditEvents, bindAudit } from './src/audit.js';
import { dashboard } from './src/dashboard.js';
import { bindHospitals, hospitals } from './src/hospitals.js';
import { bindInquiries, inquiries } from './src/inquiries.js';
import { aiStatus, bindOperations, operations, readiness } from './src/operations.js';
import { bindReviews, reviews } from './src/reviews.js';
import { state } from './src/state.js';
import { $, message, run, showTab, signedOut } from './src/ui.js';

$('#login-form').onsubmit = e => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.target)); void run(async () => { const result = await request('/api/wc/admin/auth/login', data); state.token = result.token; e.target.reset(); $('#login-panel').hidden = true; $('#workspace').hidden = false; $('#logout').hidden = false; showTab('inquiries'); await dashboard(); await inquiries(); }); };

$('#logout').onclick = () => run(async () => { try {
    await request('/api/wc/admin/auth/logout', {});
}
finally {
    signedOut();
} });

document.querySelectorAll('[data-tab]').forEach(b => b.onclick = () => run(async () => { showTab(b.dataset.tab); if (state.tab === 'inquiries')
    await inquiries(); if (state.tab === 'hospitals')
    await hospitals(); if (state.tab === 'reviews')
    await reviews(); if (state.tab === 'audit')
    await auditEvents(); if (state.tab === 'operations') {
    await operations();
    await aiStatus();
    await readiness();
} await dashboard(); }));

$('#password-form').onsubmit = e => { e.preventDefault(); const d = Object.fromEntries(new FormData(e.target)); void run(async () => { if (d.newPassword !== d.confirm)
    throw new Error('새 비밀번호가 일치하지 않습니다.'); await request('/api/wc/admin/password', { currentPassword: d.currentPassword, newPassword: d.newPassword }, 'PUT'); e.target.reset(); signedOut(); message('비밀번호가 변경되었습니다. 다시 로그인해 주세요.'); }); };

bindInquiries();

bindHospitals();

bindOperations();

bindReviews();

bindAudit();
