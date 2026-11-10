import { useEffect, useState } from "react";
import { Search, ChevronRight } from "lucide-react";
import { Modal, RiskBadge } from "../components";
import type { Store, Detail, Facility, Risk } from "../data";
import { searchFacilities } from "../services/adminApi";

const SEARCH_DELAY = 300;

export default function GlobalSearch({
  store,
  close,
  open,
}: {
  store: Store;
  close: () => void;
  open: (d: Detail) => void;
}) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLocaleLowerCase();
  // 의료기관은 수만 건이라 store에 없다. 입력이 잠시 멈추면 서버에서 찾고, 어느 검색어의 결과인지 함께 기억한다.
  const [found, setFound] = useState<{ query: string; items: Facility[] }>({
    query: "",
    items: [],
  });
  const [pending, setPending] = useState(false);
  useEffect(() => {
    if (!q) {
      setPending(false);
      return;
    }
    let cancelled = false;
    setPending(true);
    const timer = setTimeout(() => {
      searchFacilities({ q, size: 5 })
        .then((result) => {
          if (!cancelled) setFound({ query: q, items: result.items });
        })
        .catch(() => {
          if (!cancelled) setFound({ query: q, items: [] }); // 의료기관 결과만 비우고 나머지 검색은 유지
        })
        .finally(() => {
          if (!cancelled) setPending(false);
        });
    }, SEARCH_DELAY);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [q]);
  const facilities = found.query === q ? found.items : [];
  const results: {
    detail: Detail;
    title: string;
    subtitle: string;
    risk?: Risk;
  }[] = q
    ? [
        ...store.diagnoses
          .filter((d) =>
            `${d.id} ${d.userId} ${d.condition} ${d.area}`
              .toLocaleLowerCase()
              .includes(q),
          )
          .slice(0, 5)
          .map((d) => ({
            detail: { type: "diagnosis" as const, id: d.id },
            title: d.id,
            subtitle: `${d.userId} · ${d.condition} · ${d.area}`,
            risk: d.risk,
          })),
        ...store.users
          .filter((u) => `${u.id} ${u.name}`.toLocaleLowerCase().includes(q))
          .slice(0, 5)
          .map((u) => ({
            detail: { type: "user" as const, id: u.id },
            title: u.name,
            subtitle: `사용자 · ${u.id}`,
          })),
        ...facilities.map((f) => ({
          detail: { type: "facility" as const, id: f.id, facility: f },
          title: f.name,
          subtitle: `의료기관 · ${f.region} · ${f.type}`,
        })),
        ...store.inquiries
          .filter((i) =>
            `${i.id} ${i.subject} ${i.author}`.toLocaleLowerCase().includes(q),
          )
          .slice(0, 5)
          .map((i) => ({
            detail: { type: "inquiry" as const, id: i.id },
            title: i.subject,
            subtitle: `문의 #${i.id} · ${i.author}`,
          })),
      ]
    : [];
  return (
    <Modal title="전체 검색" close={close} wide>
      <label className="global-search-input">
        <Search size={19} />
        <input
          autoFocus
          aria-label="전체 검색어"
          placeholder="진단 ID, 사용자, 의료기관 또는 문의 검색"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      <div className="global-results">
        {!q ? (
          <p className="search-placeholder">
            찾고 싶은 사용자나 기록을 입력해 주세요.
          </p>
        ) : results.length ? (
          <>
            <p className="search-result-count">유형별 최대 5개씩 표시합니다.</p>
            {results.map((result) => (
              <button
                key={`${result.detail.type}-${"id" in result.detail ? result.detail.id : ""}`}
                onClick={() => open(result.detail)}
              >
                <div>
                  <strong>{result.title}</strong>
                  <small>{result.subtitle}</small>
                </div>
                {result.risk && <RiskBadge risk={result.risk} />}
                <ChevronRight size={15} />
              </button>
            ))}
          </>
        ) : pending ? (
          <p className="search-placeholder" role="status">
            검색 중입니다
          </p>
        ) : (
          <p className="search-placeholder">검색 결과가 없습니다.</p>
        )}
      </div>
    </Modal>
  );
}
