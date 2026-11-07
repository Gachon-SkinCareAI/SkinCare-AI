import { request } from './api.js';
import { AUDIT_ACTIONS } from './constants.js';
import { $, element, pages, run } from './ui.js';

export async function auditEvents(page = 0) {
    const d = await request(`/api/wc/admin/audit?page=${page}&size=20`), root = $('#audit-list');
    root.replaceChildren();
    if (!d.items.length)
        root.append(element('p', '기록된 감사 이력이 없습니다.', 'card'));
    for (const item of d.items) {
        const card = element('article', undefined, 'card');
        card.append(element('h3', AUDIT_ACTIONS[item.action] || item.action), element('p', `관리자 ${item.actorId} · ${item.occurredAt.replace('T', ' ')}`), element('p', '대상 ' + item.targetId, 'record-id'));
        if (item.detail)
            card.append(element('p', item.detail, 'record-id'));
        root.append(card);
    }
    pages($('#audit-pages'), d, auditEvents);
}

export function bindAudit() {
$('#audit-refresh').onclick = () => run(() => auditEvents());
}
