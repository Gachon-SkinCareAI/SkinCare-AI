import { useEffect, useRef, useState } from "react";
import { RefreshCw } from "lucide-react";
import { Badge, Empty, Metric, Pagination, SearchInput } from "../components";
import type { Facility, OpenDetail, Store } from "../data";
import { searchFacilities } from "../services/adminApi";

// 화면 문구 → 서버 유형 코드
const TYPES: [string, string][] = [
  ["모든 종류", ""],
  ["피부과", "DERMATOLOGY"],
  ["종합병원", "GENERAL_HOSPITAL"],
  ["병원/의원", "OTHER"],
  ["약국", "PHARMACY"],
];

// 화면 문구 → 서버 운영 상태 코드
const STATUSES: [string, string][] = [
  ["모든 상태", ""],
  ["운영 중", "ACTIVE"],
  ["운영 중지·숨김", "INACTIVE"],
];

const PAGE_SIZE = 20;
const SEARCH_DELAY = 300;

type Loaded = { items: Facility[]; total: number };

export default function Facilities({
  store,
  open,
  refreshKey = 0,
}: {
  store: Store;
  open: OpenDetail;
  // 동기화처럼 목록이 바뀌는 작업이 끝나면 값이 올라가 목록을 다시 불러온다
  refreshKey?: number;
}) {
  const m = store.dashboard;
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState(""); // 입력이 잠시 멈춘 뒤 서버로 보내는 검색어
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1); // 화면 페이지는 1부터, 서버는 0부터
  const [loaded, setLoaded] = useState<Loaded>({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const latest = useRef(0);

  // 입력할 때마다 서버를 부르지 않도록 잠시 기다린다. 검색어가 바뀌면 첫 페이지로 돌아간다.
  useEffect(() => {
    if (query.trim() === search) return;
    const timer = setTimeout(() => {
      setSearch(query.trim());
      setPage(1);
    }, SEARCH_DELAY);
    return () => clearTimeout(timer);
  }, [query, search]);

  // 검색·필터·페이지가 바뀌면 서버에서 해당 페이지만 불러온다. 늦게 도착한 이전 응답은 버린다.
  useEffect(() => {
    const request = ++latest.current;
    setLoading(true);
    setError("");
    searchFacilities({
      q: search,
      type,
      status,
      page: page - 1,
      size: PAGE_SIZE,
    })
      .then((result) => {
        if (request === latest.current)
          setLoaded({ items: result.items, total: result.total });
      })
      .catch((cause) => {
        if (request === latest.current)
          setError(
            cause instanceof Error
              ? cause.message
              : "의료기관 목록을 불러오지 못했습니다",
          );
      })
      .finally(() => {
        if (request === latest.current) setLoading(false);
      });
  }, [search, type, status, page, refreshKey, retry]);

  const pages = Math.max(1, Math.ceil(loaded.total / PAGE_SIZE));
  return (
    <>
      <section className="metrics-grid three" aria-label="의료기관 지표">
        <Metric
          icon="🏥"
          value={(m.activeHospitals ?? 0).toLocaleString()}
          label="등록 의료기관"
          hint="운영 중인 피부과 · 병원 · 약국"
        />
        <Metric
          icon="🌙"
          value={(m.activeNightCare ?? 0).toLocaleString()}
          label="야간진료 가능"
          hint="야간진료로 등록된 기관"
          color="purple"
        />
        <Metric
          icon="💊"
          value={(m.activePharmacies ?? 0).toLocaleString()}
          label="약국"
          hint="등록된 약국"
          color="green"
        />
      </section>
      <section className="panel table-panel" aria-busy={loading}>
        <div className="panel-heading facility-heading">
          <div>
            <h2>의료기관 목록</h2>
            <p>이름순으로 서버에서 검색합니다</p>
          </div>
          <button
            className="button primary"
            onClick={() => open({ type: "sync" })}
          >
            <RefreshCw size={16} />
            공공데이터 동기화
          </button>
        </div>
        <div className="table-filters">
          <SearchInput
            label="의료기관 검색"
            placeholder="기관명, 지역 또는 주소 검색"
            value={query}
            onChange={setQuery}
          />
          <label className="select-field">
            <span className="sr-only">의료기관 종류</span>
            <select
              aria-label="의료기관 종류"
              value={type}
              onChange={(e) => {
                setType(e.target.value);
                setPage(1);
              }}
            >
              {TYPES.map(([label, value]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="select-field">
            <span className="sr-only">운영 상태</span>
            <select
              aria-label="운영 상태"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
            >
              {STATUSES.map(([label, value]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {error ? (
          <div className="empty">
            <p role="alert">{error}</p>
            <button
              className="button secondary"
              onClick={() => setRetry((n) => n + 1)}
            >
              다시 시도
            </button>
          </div>
        ) : loaded.items.length ? (
          <div className="table-scroll" style={{ opacity: loading ? 0.6 : 1 }}>
            <table>
              <thead>
                <tr>
                  <th>기관명</th>
                  <th>종류</th>
                  <th>지역</th>
                  <th>야간진료</th>
                  <th>응급실</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {loaded.items.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <button
                        className="table-link navy-link"
                        aria-label={`${f.name} 상세 보기`}
                        onClick={() =>
                          open({ type: "facility", id: f.id, facility: f })
                        }
                      >
                        {f.name}
                      </button>
                    </td>
                    <td>{f.type}</td>
                    <td>{f.region}</td>
                    <td>
                      <Badge tone={f.night ? "purple" : "gray"}>
                        {f.night ? "가능" : "-"}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={f.emergency ? "red" : "gray"}>
                        {f.emergency ? "있음" : "-"}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={f.active ? "green" : "gray"}>
                        {f.active ? "운영 중" : "중지·숨김"}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : loading ? (
          <p className="muted detail-empty" role="status">
            의료기관을 불러오는 중입니다
          </p>
        ) : (
          <Empty />
        )}
        {!error && (
          <Pagination
            count={loaded.total}
            page={Math.min(page, pages)}
            total={pages}
            unit="개 기관"
            onChange={setPage}
          />
        )}
      </section>
    </>
  );
}
