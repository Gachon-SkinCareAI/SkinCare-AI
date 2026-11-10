export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function createClient(baseUrl, token, transport = fetch) {
  const base = baseUrl.replace(/\/$/, "");
  const parsed = new URL(base);
  if (
    !["http:", "https:"].includes(parsed.protocol) ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash
  )
    throw new Error("올바른 서버 주소를 설정해 주세요.");
  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 95_000);
    const headers = new Headers(options.headers);
    const bearer = token();
    if (bearer) headers.set("Authorization", `Bearer ${bearer}`);
    try {
      const response = await transport(base + path, {
        ...options,
        headers,
        signal: controller.signal,
      });
      if (response.status === 204) return undefined;
      const value = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message =
          response.status === 401
            ? "연결 인증이 만료되었어요. 마이에서 연결 상태를 다시 확인해 주세요."
            : [502, 504, 530].includes(response.status)
              ? "시연 서버의 공개 연결이 끊겼거나 응답이 지연되고 있습니다. 운영자에게 서버 실행 상태를 확인해 주세요. 연결이 복구되면 내 기록에서 문진을 이어갈 수 있습니다."
              : response.status === 503
                ? "서버가 준비 중이거나 잠시 이용할 수 없습니다. 잠시 후 내 기록에서 문진을 이어가 주세요."
                : typeof value.message === "string"
                  ? value.message
                  : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        throw new ApiError(response.status, message);
      }
      return value;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(
        0,
        "서버에 연결하지 못했습니다. 연결을 확인한 뒤 기록을 새로 불러오거나 다시 시도해 주세요.",
      );
    } finally {
      clearTimeout(timeout);
    }
  }
  const post = (path, body, method = "POST") =>
    request(path, {
      method,
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
  return {
    base,
    request,
    post,
    list: (page = 0, caseId) =>
      request(
        `/api/wc/analyses?page=${page}&size=20${caseId ? `&caseId=${encodeURIComponent(caseId)}` : ""}`,
      ),
    get: (id) => request(`/api/wc/analyses/${encodeURIComponent(id)}`),
    create: (form) =>
      request("/api/wc/analyses", {
        method: "POST",
        body: form,
      }),
    answer: (id, body) =>
      post(`/api/wc/analyses/${encodeURIComponent(id)}/answers`, body),
    retry: (session) =>
      post(`/api/wc/analyses/${encodeURIComponent(session.id)}/retry`, {
        expectedVersion: session.version,
      }),
    progress: (id, expectedVersion, body) =>
      post(
        `/api/wc/analyses/${encodeURIComponent(id)}/progress`,
        {
          expectedVersion,
          ...body,
        },
        "PUT",
      ),
    comparison: (id, referenceId) =>
      request(
        `/api/wc/analyses/${encodeURIComponent(id)}/comparison${referenceId ? `?referenceId=${encodeURIComponent(referenceId)}` : ""}`,
      ),
    remove: (id) =>
      request(`/api/wc/analyses/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
  };
}
