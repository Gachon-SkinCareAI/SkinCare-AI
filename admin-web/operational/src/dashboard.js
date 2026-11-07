import { request } from './api.js';
import { $, element } from './ui.js';

export async function dashboard() { const d = await request('/api/wc/admin/dashboard'); const root = $('#dashboard'); root.replaceChildren(); for (const [key, label] of [['waitingInquiries', '답변 대기'], ['completedInquiries', '답변 완료'], ['activeHospitals', '공개 기관'], ['inactiveHospitals', '숨긴 기관']]) {
    const card = element('div', undefined, 'stat');
    card.append(element('span', label), element('strong', String(d[key])));
    root.append(card);
} }
