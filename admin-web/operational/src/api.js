import { state } from './state.js';
import { signedOut } from './ui.js';

export async function request(path, body, method = body ? 'POST' : 'GET') {
    const headers = { Accept: 'application/json' };
    if (state.token)
        headers.Authorization = 'Bearer ' + state.token;
    if (body !== undefined)
        headers['Content-Type'] = 'application/json';
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 30000);
    try {
        const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: abort.signal, cache: 'no-store' });
        const value = response.status === 204 ? null : await response.json().catch(() => ({}));
        if (!response.ok) {
            if (response.status === 401)
                signedOut();
            throw new Error(value?.message || (response.status === 403 ? '관리자 권한이 필요합니다.' : '요청을 완료하지 못했습니다.'));
        }
        return value;
    }
    finally {
        clearTimeout(timer);
    }
}
