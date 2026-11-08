// 백엔드 담당자와 아래 주소, 경로, 요청/응답 형식을 맞추세요.
// 실제 인증 없이 성공을 반환하는 임시 로그인은 사용하지 않습니다.
export const AUTH_CONFIG = {
  baseUrl: '', // 예: https://api.example.com (공개 주소이며 비밀 키를 넣지 마세요)
  loginPath: '/api/auth/login',
  signupPath: '/api/auth/signup',
};

async function request(path, body) {
  if (!AUTH_CONFIG.baseUrl) throw new Error('인증 서버가 아직 연결되지 않았습니다. src/authApi.js의 baseUrl을 설정해 주세요.');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`${AUTH_CONFIG.baseUrl.replace(/\/$/, '')}${path}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body), signal: controller.signal,
    });
    if (!response.ok) {
      const messages = { 400: '입력 내용을 확인해 주세요.', 401: '이메일 또는 비밀번호가 올바르지 않습니다.', 403: '이 계정으로 로그인할 수 없습니다.', 409: '이미 가입된 이메일입니다.', 422: '입력 형식이 서버 기준과 맞지 않습니다.', 429: '요청이 많습니다. 잠시 후 다시 시도해 주세요.' };
      throw new Error(messages[response.status] || '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
    }
    const text = await response.text();
    if (!text) return null;
    try { return JSON.parse(text); }
    catch { throw new Error('서버 응답 형식을 확인해 주세요. JSON 응답이 필요합니다.'); }
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('응답 시간이 초과되었습니다. 다시 시도해 주세요.');
    if (error instanceof TypeError) throw new Error('서버에 연결할 수 없습니다. 네트워크와 서버 주소를 확인해 주세요.');
    throw error;
  } finally { clearTimeout(timer); }
}

export async function login({ email, password }) {
  const result = await request(AUTH_CONFIG.loginPath, { email: email.trim(), password });
  // 가정: { accessToken: string, user?: { name?: string, email?: string } }
  if (!result || typeof result.accessToken !== 'string' || !result.accessToken.trim()) {
    throw new Error('로그인 응답에 accessToken이 없습니다. 백엔드 응답 형식을 확인해 주세요.');
  }
  return result;
}

export function signup({ name, email, password }) {
  return request(AUTH_CONFIG.signupPath, { name: name.trim(), email: email.trim(), password });
}
