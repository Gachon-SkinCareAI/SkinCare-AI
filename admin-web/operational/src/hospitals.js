import { request } from './api.js';
import { TYPES } from './constants.js';
import { dashboard } from './dashboard.js';
import { state } from './state.js';
import { $, element, message, pages, run } from './ui.js';

export async function hospitals(page = 0) {
    const d = await request(`/api/wc/admin/hospitals?q=${encodeURIComponent(state.hospitalQuery)}&page=${page}&size=20`);
    state.hospitalPage = page;
    const root = $('#hospital-list');
    root.replaceChildren();
    if (!d.items.length)
        root.append(element('p', '등록된 기관이 없습니다. 확인한 기관 정보를 등록해 주세요.', 'card'));
    for (const h of d.items) {
        const b = element('button', undefined, 'card list-card');
        b.append(element('span', h.status === 'ACTIVE' ? '공개' : '숨김', 'badge'), element('h3', h.name), element('p', h.address), element('p', `${TYPES[h.type]} · 확인일 ${h.verifiedOn || '미확인'}`, 'muted'));
        b.onclick = () => editHospital(h);
        root.append(b);
    }
    pages($('#hospital-pages'), d, hospitals);
}

export function editHospital(h = null) {
    state.editingHospital = h;
    state.hospitalRequest = null;
    const form = $('#hospital-form');
    form.reset();
    $('#hospital-editor').hidden = false;
    $('#hospital-editor-title').textContent = h ? '기관 정보 수정' : '새 기관 등록';
    if (h)
        for (const [key, value] of Object.entries(h)) {
            const f = form.elements.namedItem(key);
            if (f) {
                if (f.type === 'checkbox')
                    f.checked = !!value;
                else
                    f.value = value ?? '';
            }
        }
    $('#hospital-editor').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

export function bindHospitals() {
$('#hospital-search-form').onsubmit = e => { e.preventDefault(); state.hospitalQuery = $('#hospital-query').value.trim(); void run(() => hospitals()); };
$('#hospital-new').onclick = () => editHospital();
$('#hospital-cancel').onclick = () => { $('#hospital-editor').hidden = true; state.editingHospital = null; };
$('#hospital-form').onsubmit = e => {
    e.preventDefault();
    const f = e.target;
    const body = Object.fromEntries(new FormData(f));
    void run(async () => {
        body.nightCare = f.elements.nightCare.checked;
        body.emergency = f.elements.emergency.checked;
        body.expectedVersion = state.editingHospital?.version ?? null;
        if (!state.editingHospital) {
            const fingerprint = JSON.stringify(body);
            if (state.hospitalRequest?.fingerprint !== fingerprint)
                state.hospitalRequest = { id: crypto.randomUUID(), fingerprint };
            body.clientRequestId = state.hospitalRequest.id;
        }
        const saved = await request('/api/wc/admin/hospitals' + (state.editingHospital ? '/' + encodeURIComponent(state.editingHospital.id) : ''), body, state.editingHospital ? 'PUT' : 'POST');
        editHospital(saved);
        await hospitals(state.hospitalPage);
        await dashboard();
        message('기관 정보를 저장했습니다.');
    });
};
}
