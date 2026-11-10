import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Search,
  X,
} from "lucide-react";
import type { Risk } from "./data";

export function Metric({
  icon,
  value,
  label,
  hint,
  change,
  color = "navy",
}: {
  icon: string;
  value: string;
  label: string;
  hint: string;
  change?: string;
  color?: string;
}) {
  return (
    <article className="metric-card">
      <div className="metric-top">
        <span className={`metric-icon ${color}`}>{icon}</span>
        {change && (
          <span
            className={`metric-change ${change.startsWith("-") ? "negative" : ""}`}
          >
            {change}
          </span>
        )}
      </div>
      <strong className={`metric-value text-${color}`}>{value}</strong>
      <div className="metric-label">{label}</div>
      <div className="metric-hint">{hint}</div>
    </article>
  );
}
export function Badge({
  children,
  tone = "green",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
export function RiskBadge({ risk }: { risk: Risk }) {
  return (
    <Badge
      tone={risk === "응급" ? "red" : risk === "경고" ? "orange" : "green"}
    >
      {risk}
    </Badge>
  );
}
export function SearchInput({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (s: string) => void;
  placeholder?: string;
}) {
  return (
    <label className="search-field">
      <Search size={15} />
      <input
        aria-label={label}
        placeholder={placeholder || label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button
          type="button"
          aria-label={`${label} 지우기`}
          onClick={() => onChange("")}
        >
          <X size={13} />
        </button>
      )}
    </label>
  );
}
export function Tabs({
  items,
  value,
  onChange,
  label,
}: {
  items: readonly string[];
  value: string;
  onChange: (s: string) => void;
  label: string;
}) {
  return (
    <div className="tabs" role="group" aria-label={label}>
      {items.map((item) => (
        <button
          key={item}
          className={value === item ? "selected" : ""}
          aria-pressed={value === item}
          onClick={() => onChange(item)}
        >
          {item}
        </button>
      ))}
    </div>
  );
}
export function usePagination<T>(rows: T[], pageSize = 5) {
  const [requested, setPage] = useState(1);
  const total = Math.max(1, Math.ceil(rows.length / pageSize));
  const page = Math.min(requested, total);
  return {
    page,
    total,
    setPage,
    visible: rows.slice((page - 1) * pageSize, page * pageSize),
  };
}
export function Pagination({
  count,
  page,
  total,
  onChange,
  unit = "건",
}: {
  count: number;
  page: number;
  total: number;
  onChange: (n: number) => void;
  unit?: string;
}) {
  // 페이지 번호는 5개씩 묶어서 보여준다: 1~5, 6~10, 11~15 ... (마지막 묶음은 남은 페이지까지만)
  const start = pageGroupStart(page);
  const numbers = Array.from(
    { length: Math.max(0, Math.min(PAGE_GROUP, total - start + 1)) },
    (_, i) => start + i,
  );
  return (
    <div className="pagination">
      <span>
        전체 {count.toLocaleString()}
        {unit} · {page} / {total} 페이지
      </span>
      <nav aria-label="표 페이지 이동">
        <button
          className="step"
          aria-label="첫 페이지"
          disabled={page === 1}
          onClick={() => onChange(1)}
        >
          <ChevronsLeft size={13} />
        </button>
        <button
          className="step"
          aria-label="이전 페이지"
          disabled={page === 1}
          onClick={() => onChange(page - 1)}
        >
          <ChevronLeft size={13} />
          <span>이전</span>
        </button>
        {numbers.map((n) => (
          <button
            key={n}
            aria-label={`${n}페이지`}
            aria-current={n === page ? "page" : undefined}
            className={page === n ? "current" : ""}
            onClick={() => onChange(n)}
          >
            {n}
          </button>
        ))}
        <button
          className="step"
          aria-label="다음 페이지"
          disabled={page === total}
          onClick={() => onChange(page + 1)}
        >
          <span>다음</span>
          <ChevronRight size={13} />
        </button>
        <button
          className="step"
          aria-label="마지막 페이지"
          disabled={page === total}
          onClick={() => onChange(total)}
        >
          <ChevronsRight size={13} />
        </button>
      </nav>
    </div>
  );
}

// 한 번에 보여주는 페이지 번호 수
export const PAGE_GROUP = 5;

// 현재 페이지가 속한 묶음의 첫 번호 (1~5 → 1, 6~10 → 6, 11~15 → 11 ...)
export function pageGroupStart(page: number) {
  return Math.floor((Math.max(1, page) - 1) / PAGE_GROUP) * PAGE_GROUP + 1;
}
export function Empty({
  text = "검색어나 필터를 바꿔 다시 확인해 주세요.",
}: {
  text?: string;
}) {
  return (
    <div className="empty">
      <Search size={28} />
      <strong>검색 결과가 없습니다</strong>
      <p>{text}</p>
    </div>
  );
}
export function Modal({
  title,
  close,
  children,
  wide = false,
}: {
  title: string;
  close: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? "wide" : ""}`}
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="modal-heading">
        <h2>{title}</h2>
        <button className="icon-button" aria-label="닫기" onClick={close}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Progress({
  value,
  tone = "navy",
}: {
  value: number;
  tone?: string;
}) {
  return (
    <span className={`progress progress-${tone}`}>
      <span style={{ width: `${value}%` }} />
    </span>
  );
}
