import { request } from './api.js';
import { ANALYSIS_STATES } from './constants.js';
import { $, element, pages, run } from './ui.js';

export async function operations(page = 0) {
    const filter = $('#analysis-status').value;
    const result = await request(`/api/wc/admin/operations?page=${page}&size=20${filter ? '&status=' + encodeURIComponent(filter) : ''}`);
    const counts = $('#analysis-counts');
    counts.replaceChildren();
    for (const [key, label] of Object.entries(ANALYSIS_STATES)) {
        const card = element('div', undefined, 'stat');
        card.append(element('span', label), element('strong', String(result.counts[key] ?? 0)));
        counts.append(card);
    }
    const root = $('#analysis-list');
    root.replaceChildren();
    if (!result.items.length)
        root.append(element('p', '해당 상태의 분석 기록이 없습니다.', 'card'));
    for (const item of result.items) {
        const card = element('article', undefined, 'card');
        card.append(element('span', ANALYSIS_STATES[item.status] || '상태 확인 필요', 'badge'), element('p', '기록 번호: ' + item.id, 'record-id'), element('p', `기록일 ${item.createdAt?.slice(0, 16).replace('T', ' ')} · 저장된 문진 ${item.turn}회`, 'muted'));
        if (item.errorCode)
            card.append(element('p', 'AI 연결을 다시 확인해야 합니다. 사용자가 기록에서 이어갈 수 있습니다.', 'muted'));
        root.append(card);
    }
    pages($('#analysis-pages'), result, operations);
}

export async function aiStatus() {
    const root = $('#ai-status');
    root.replaceChildren(element('p', 'AI 서버 상태를 확인하고 있습니다.'));
    try {
        const status = await request('/api/wc/admin/operations/ai-status');
        const labels = { READY: '모델 적재 및 기준값 준비됨', DEGRADED: 'AI 준비 상태 확인 필요', UNAVAILABLE: 'AI 서버 연결 실패', AUTH_ERROR: 'AI 서버 인증 설정 확인 필요', UNCONFIGURED: 'AI 연결 토큰 미설정', INVALID_RESPONSE: 'AI 상태 응답 확인 필요' };
        root.replaceChildren(element('strong', labels[status.status] || '상태 확인 필요'));
        if (status.checkedAt)
            root.append(element('p', '확인 시각: ' + new Date(status.checkedAt).toLocaleString('ko-KR'), 'muted'));
        if (status.reportedModels)
            root.append(element('p', `등록 탐지 모델: ${status.reportedModels.detect}`), element('p', `등록 분류 모델: ${status.reportedModels.classify}`));
        if (status.bankVersion)
            root.append(element('p', `질문 목록 버전: ${status.bankVersion} · 안내 카드 버전: ${status.cardVersion}`, 'muted'));
        if (typeof status.llmConfigured === 'boolean')
            root.append(element('p', status.llmConfigured ? '선택형 LLM 연결 설정 있음 (응답 상태 별도 확인)' : '선택형 LLM 연결 미설정 · 질문 목록과 규칙 흐름 사용', 'muted'));
        if (typeof status.inflight === 'number')
            root.append(element('p', `현재 이미지 처리 요청: ${status.inflight}${typeof status.max_inflight === 'number' ? ' / ' + status.max_inflight : ''}`, 'muted'));
    }
    catch (error) {
        root.replaceChildren(element('p', '상태를 확인하지 못했습니다. 다시 조회해 주세요.'));
        throw error;
    }
}

export async function readiness() {
    const data = await request('/api/wc/admin/operations/readiness'), root = $('#readiness-status');
    root.replaceChildren();
    const labels = { CONFIGURED: '설정됨 · 실제 검증 필요', MISSING: '미설정', INVALID: '설정 확인 필요', INCOMPLETE: '일부 설정 누락', DISABLED: '비활성', PENDING_REVIEW: '검토 대기' };
    for (const check of data.checks) {
        const card = element('article', undefined, 'card');
        card.append(element('strong', check.label), element('p', labels[check.status] || '확인 필요', 'badge'), element('p', check.nextAction, 'muted'));
        root.append(card);
    }
    root.append(element('p', '설정 조회 시각 ' + new Date(data.checkedAt).toLocaleString('ko-KR'), 'muted'));
}

export function bindOperations() {
$('#operations-refresh').onclick = () => run(() => operations());
$('#analysis-search').onclick = () => run(() => operations());
$('#ai-refresh').onclick = () => run(aiStatus);
$('#readiness-refresh').onclick = () => run(readiness);
}
