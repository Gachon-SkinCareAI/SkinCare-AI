import { request } from './api.js';
import { dashboard } from './dashboard.js';
import { state } from './state.js';
import { $, element, message, pages, run } from './ui.js';

export async function inquiries(page = 0) {
    const status = $('#inquiry-status').value;
    const d = await request(`/api/wc/admin/inquiries?page=${page}&size=20${status ? '&status=' + encodeURIComponent(status) : ''}`);
    state.inquiryPage = page;
    const root = $('#inquiry-list');
    root.replaceChildren();
    if (!d.items.length)
        root.append(element('p', '해당 상태의 문의가 없습니다.', 'card'));
    for (const i of d.items) {
        const b = element('button', undefined, 'card list-card');
        b.append(element('span', i.status === 'WAITING' ? '답변 대기' : '답변 완료', 'badge'), element('h3', i.title), element('span', i.createdAt?.slice(0, 16).replace('T', ' '), 'muted'));
        b.onclick = () => run(() => inquiry(i.id));
        root.append(b);
    }
    pages($('#inquiry-pages'), d, inquiries);
}

export async function inquiry(id) {
    const i = await request('/api/wc/admin/inquiries/' + encodeURIComponent(id));
    state.selectedInquiry = i;
    state.replyRequest = null;
    const root = $('#inquiry-detail');
    root.hidden = false;
    root.replaceChildren(element('span', i.status === 'WAITING' ? '답변 대기' : '답변 완료', 'badge'), element('h3', i.title), element('p', i.content, 'pre'));
    if (i.analysisId)
        root.append(element('p', '연결된 분석 기록 번호: ' + i.analysisId, 'muted'));
    if (i.answer)
        root.append(element('h3', '등록된 답변'), element('p', i.answer.content, 'pre'), element('p', i.answer.createdAt?.slice(0, 16).replace('T', ' '), 'muted'));
    else {
        const form = element('form');
        const label = element('label', '운영팀 답변');
        const input = element('textarea');
        input.name = 'content';
        input.required = true;
        input.maxLength = 5000;
        label.append(input);
        const submit = element('button', '답변 등록');
        form.append(label, element('p', '서비스 지원 답변입니다. 확인되지 않은 의료 판단을 작성하지 마세요.', 'muted'), submit);
        form.onsubmit = e => {
            e.preventDefault();
            void run(async () => {
                const text = input.value.trim();
                if (!text)
                    throw new Error('답변을 입력해 주세요.');
                if (state.replyRequest?.content !== text)
                    state.replyRequest = { id: crypto.randomUUID(), content: text };
                const result = await request('/api/wc/admin/inquiries/' + encodeURIComponent(i.id) + '/answer', { clientRequestId: state.replyRequest.id, expectedVersion: i.version, content: text }, 'PUT');
                await inquiry(result.id);
                await inquiries(state.inquiryPage);
                await dashboard();
                message('답변을 등록했습니다.');
            });
        };
        root.append(form);
    }
    const reload = element('button', '문의 다시 불러오기', 'secondary');
    reload.onclick = () => run(() => inquiry(id));
    root.append(reload);
    root.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

export function bindInquiries() {
$('#inquiry-search').onclick = () => run(() => inquiries());
}
