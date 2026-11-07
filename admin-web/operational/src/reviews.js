import { request } from './api.js';
import { REVIEW_KINDS, REVIEW_STATES } from './constants.js';
import { state } from './state.js';
import { $, element, message, pages, run } from './ui.js';

export async function reviews(page = 0) {
    state.reviewCapabilities = await request('/api/wc/admin/reviews/capabilities');
    $('#review-capabilities').textContent = state.reviewCapabilities.clinicalReviewer ? '이 계정은 임상 검수 담당자로 지정되어 있습니다. 실제 검수 근거를 확인한 뒤 기록하세요.' : '이 계정에는 임상 승인 권한이 없습니다. 자료 준비·검수 요청·타인 자료 반려가 가능합니다.';
    const status = $('#review-status').value, d = await request(`/api/wc/admin/reviews?page=${page}&size=20${status ? '&status=' + status : ''}`), root = $('#review-list');
    root.replaceChildren();
    if (!d.items.length)
        root.append(element('p', '해당 상태의 검수 자료가 없습니다.', 'card'));
    for (const item of d.items) {
        const b = element('button', undefined, 'card list-card');
        b.append(element('span', REVIEW_STATES[item.status], 'badge'), element('h3', item.title), element('p', REVIEW_KINDS[item.kind] + ' · 작성자 ' + item.authorId), element('p', item.contentHash, 'record-id'));
        b.onclick = () => run(() => reviewDetail(item.id));
        root.append(b);
    }
    pages($('#review-pages'), d, reviews);
}

export function editReview(previous = null) {
    state.reviewRequest = null;
    const form = $('#review-form');
    form.reset();
    $('#review-editor').hidden = false;
    if (previous) {
        form.elements.kind.value = previous.kind;
        form.elements.title.value = previous.title;
        form.elements.content.value = previous.content;
        form.elements.sourceUrl.value = previous.sourceUrl;
        form.elements.supersedesId.value = previous.id;
    }
    $('#review-editor').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

export async function reviewDetail(id) {
    const d = await request('/api/wc/admin/reviews/' + encodeURIComponent(id)), root = $('#review-detail');
    root.hidden = false;
    root.replaceChildren();
    root.append(element('span', REVIEW_STATES[d.status], 'badge'), element('h3', d.title), element('p', `작성자 ${d.authorId} · ${REVIEW_KINDS[d.kind]} · 버전 ${d.version}`), element('p', '자료 번호 ' + d.id, 'record-id'), element('p', 'SHA-256 ' + d.contentHash, 'record-id'));
    if (d.supersedesId)
        root.append(element('p', '이전 검수 ' + d.supersedesId, 'record-id'));
    const link = element('a', '원문 출처');
    link.href = d.sourceUrl;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    root.append(link);
    const details = element('details'), summary = element('summary', '저장된 원문 확인'), pre = element('pre', d.content, 'review-content');
    details.append(summary, pre);
    root.append(details);
    if (d.reviewerId)
        root.append(element('p', '검수자 ' + d.reviewerId), element('p', '검수 근거: ' + d.reason), element('p', '근거 URL: ' + (d.evidenceUrl || '없음')), element('p', '기록 시각 ' + d.decidedAt));
    const copy = element('button', '수정본을 새 검수로 작성', 'secondary');
    copy.onclick = () => editReview(d);
    root.append(copy);
    if (d.status === 'DRAFT' && d.authorId === state.reviewCapabilities.actorId) {
        const submit = element('button', '검수 요청');
        submit.onclick = () => run(async () => { await request('/api/wc/admin/reviews/' + d.id + '/state', { expectedVersion: d.version, action: 'SUBMIT' }, 'PUT'); await reviewDetail(d.id); await reviews(); message('검수를 요청했습니다. 원문은 고정되며 서비스에는 자동 반영되지 않습니다.'); });
        root.append(submit);
    }
    if (d.status === 'SUBMITTED' && d.authorId !== state.reviewCapabilities.actorId) {
        const form = element('form'), reasonLabel = element('label', '검수 근거'), reason = element('textarea'), urlLabel = element('label', '승인 근거 URL'), url = element('input');
        reason.required = true;
        reason.maxLength = 2000;
        reason.rows = 4;
        url.type = 'url';
        url.maxLength = 1000;
        reasonLabel.append(reason);
        urlLabel.append(url);
        form.append(reasonLabel, urlLabel);
        const approve = element('button', '승인 기록'), reject = element('button', '반려', 'secondary');
        approve.value = 'APPROVE';
        reject.value = 'REJECT';
        if (d.kind !== 'POLICY' && !state.reviewCapabilities.clinicalReviewer) {
            approve.disabled = true;
            approve.dataset.permanentDisabled = 'true';
        }
        form.append(approve, reject);
        form.onsubmit = e => { e.preventDefault(); const action = e.submitter?.value; if (!action)
            return; void run(async () => { await request('/api/wc/admin/reviews/' + d.id + '/state', { expectedVersion: d.version, action, reason: reason.value, evidenceUrl: url.value || null }, 'PUT'); await reviewDetail(d.id); await reviews(); message('검수 결과를 기록했습니다. 서비스 배포는 별도 검증 후 진행합니다.'); }); };
        root.append(form);
    }
}

export function bindReviews() {
$('#review-refresh').onclick = () => run(() => reviews());
$('#review-status').onchange = () => run(() => reviews());
$('#review-new').onclick = () => editReview();
$('#review-cancel').onclick = () => { $('#review-editor').hidden = true; };
$('#review-catalog').onclick = () => run(async () => { const f = $('#review-form'), d = await request('/api/wc/admin/reviews/catalog/' + f.elements.kind.value); f.elements.content.value = d.content; f.elements.title.value = d.origin; const source = d.content.match(/https:\/\/[^\s"'<>]+/); if (source)
    f.elements.sourceUrl.value = source[0]; message('현재 빌드의 원문을 불러왔습니다. 출처를 확인하고 초안으로 저장하세요.'); });
$('#review-form').onsubmit = e => { e.preventDefault(); const body = Object.fromEntries(new FormData(e.target)); void run(async () => { const fingerprint = JSON.stringify(body); if (state.reviewRequest?.fingerprint !== fingerprint)
    state.reviewRequest = { id: crypto.randomUUID(), fingerprint }; body.clientRequestId = state.reviewRequest.id; const d = await request('/api/wc/admin/reviews', body); $('#review-editor').hidden = true; await reviews(); await reviewDetail(d.id); message('검수 초안을 저장했습니다.'); }); };
}
