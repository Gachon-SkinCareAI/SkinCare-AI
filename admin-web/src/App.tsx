import { useEffect, useState } from 'react'
import { Bell, Check, ChevronRight, Menu, Search, X } from 'lucide-react'
import { Modal, RiskBadge } from './components'
import {
  createSeed,
  parseStore,
  risks,
  STORAGE_KEY,
  type Detail,
  type Navigate,
  type Page,
  type Risk,
  type Store,
} from './data'
import Dashboard from './pages/Dashboard'
import Diagnoses from './pages/Diagnoses'
import Monitoring from './pages/Monitoring'
import Facilities from './pages/Facilities'
import Users from './pages/Users'
import Inquiries from './pages/Inquiries'
import Details from './Details'

const pages: { id: Page; title: string; icon: string }[] = [
  { id: 'dashboard', title: '대시보드', icon: '📊' },
  { id: 'diagnoses', title: '진단 로그', icon: '📋' },
  { id: 'monitoring', title: 'AI 모니터링', icon: '🤖' },
  { id: 'facilities', title: '의료기관 관리', icon: '🏥' },
  { id: 'users', title: '사용자 관리', icon: '👤' },
  { id: 'inquiries', title: '문의 관리', icon: '✉' },
]
function getRoute() {
  const [hash, query] = location.hash.slice(1).split('?')
  const id = hash === 'analyses' ? 'diagnoses' : hash
  const page: Page = pages.find((p) => p.id === id)?.id || 'dashboard'
  const risk = new URLSearchParams(query).get('risk')
  return {
    page,
    risk: risks.includes(risk as Risk) ? (risk as Risk) : undefined,
  }
}
function loadStore() {
  try {
    return parseStore(localStorage.getItem(STORAGE_KEY))
  } catch {
    return createSeed()
  }
}

export default function App() {
  const [store, setStore] = useState(loadStore)
  const [route, setRoute] = useState(getRoute)
  const [collapsed, setCollapsed] = useState(false)
  const [mobileMenu, setMobileMenu] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [notifications, setNotifications] = useState(false)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [toast, setToast] = useState('')
  const [storageError, setStorageError] = useState(false)
  const active = pages.find((p) => p.id === route.page)!
  useEffect(() => {
    const sync = () => {
      setRoute(getRoute())
      setMobileMenu(false)
      setNotifications(false)
      setDetail(null)
    }
    window.addEventListener('hashchange', sync)
    return () => window.removeEventListener('hashchange', sync)
  }, [])
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
      setStorageError(false)
    } catch {
      setStorageError(true)
    }
  }, [store])
  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(''), 4500)
      return () => clearTimeout(timer)
    }
  }, [toast])
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setMobileMenu(false)
        setNotifications(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  const navigate: Navigate = (page, risk) => {
    location.hash = `${page}${risk ? `?risk=${encodeURIComponent(risk)}` : ''}`
    setRoute({ page, risk })
    setMobileMenu(false)
    setNotifications(false)
    setDetail(null)
  }
  function toggleSidebar() {
    if (window.matchMedia('(max-width: 760px)').matches)
      setMobileMenu((v) => !v)
    else setCollapsed((v) => !v)
  }
  const pending = store.inquiries.filter((q) => !q.reply.trim())
  return (
    <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : ''}`}>
      <a
        className="skip-link"
        href="#main-content"
        onClick={(e) => {
          e.preventDefault()
          document.getElementById('main-content')?.focus()
        }}
      >
        본문으로 이동
      </a>
      {mobileMenu && (
        <button
          className="sidebar-backdrop"
          aria-label="메뉴 닫기"
          onClick={() => setMobileMenu(false)}
        />
      )}
      <aside className={`sidebar ${mobileMenu ? 'mobile-open' : ''}`}>
        <div className="brand-row">
          <a
            className="brand"
            href="#dashboard"
            onClick={(e) => {
              e.preventDefault()
              navigate('dashboard')
            }}
            aria-label="SkinCare AI 대시보드"
          >
            <img
              className="brand-mark"
              src="/branding/skincare-ai-symbol-inverse.svg"
              alt=""
              width="36"
              height="36"
            />
            <span className="brand-text">
              <img
                className="brand-wordmark"
                src="/branding/skincare-ai-wordmark-inverse.svg"
                alt="SkinCare AI"
                width="122"
                height="24"
              />
              <small>관리자 대시보드</small>
            </span>
          </a>
          <button
            className="sidebar-toggle"
            aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
            onClick={toggleSidebar}
          >
            <Menu size={18} />
          </button>
        </div>
        <nav className="main-nav" aria-label="주 메뉴">
          {pages.map((page) => (
            <a
              key={page.id}
              className={`nav-item ${page.id === route.page ? 'active' : ''}`}
              aria-current={page.id === route.page ? 'page' : undefined}
              title={page.title}
              href={`#${page.id}`}
              onClick={(e) => {
                e.preventDefault()
                navigate(page.id)
              }}
            >
              <span className="nav-icon" aria-hidden="true">
                {page.icon}
              </span>
              <span className="nav-label">{page.title}</span>
            </a>
          ))}
        </nav>
        <div className="admin-profile">
          <span className="admin-avatar">관</span>
          <div>
            <strong>관리자</strong>
            <small>admin@skincare.ai</small>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="header-title">
            <button
              className="icon-button mobile-menu-button"
              aria-label="메뉴 열기"
              onClick={() => setMobileMenu(true)}
            >
              <Menu size={22} />
            </button>
            <div>
              <h1>
                <span aria-hidden="true">{active.icon}</span> {active.title}
              </h1>
              <p>2026년 9월 29일 기준</p>
            </div>
          </div>
          <div className="header-actions">
            <span
              className="demo-chip"
              title="실제 운영 정보가 아닌 가상 데이터입니다."
            >
              샘플 데이터
            </span>
            <button
              className="global-search-trigger"
              onClick={() => setSearchOpen(true)}
              aria-label="전체 검색 열기"
            >
              <Search size={16} />
              <span>검색...</span>
            </button>
            <div className="notification-wrapper">
              <button
                className="icon-button bell-button"
                aria-label="알림 보기"
                aria-expanded={notifications}
                onClick={() => setNotifications((v) => !v)}
              >
                <Bell size={23} />
                {pending.length > 0 && <span className="notification-dot" />}
              </button>
              {notifications && (
                <div className="notification-panel">
                  <div>
                    <strong>관리자 알림</strong>
                    <button
                      className="icon-button"
                      aria-label="알림 닫기"
                      onClick={() => setNotifications(false)}
                    >
                      <X size={17} />
                    </button>
                  </div>
                  <p>
                    답변을 기다리는 샘플 문의가 {pending.length}건 있습니다.
                  </p>
                  {pending.slice(0, 3).map((q) => (
                    <button
                      className="notification-item"
                      key={q.id}
                      onClick={() => {
                        setDetail({ type: 'inquiry', id: q.id })
                        setNotifications(false)
                      }}
                    >
                      <span>
                        문의 #{q.id}
                        <small>{q.subject}</small>
                      </span>
                      <ChevronRight size={14} />
                    </button>
                  ))}
                  {!pending.length && (
                    <span className="all-done">
                      모든 샘플 문의에 답변했습니다.
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          {storageError && (
            <div role="alert" className="storage-warning">
              브라우저 저장소를 사용할 수 없습니다. 변경 내용은 현재 탭에서만
              유지됩니다.
            </div>
          )}
          {route.page === 'dashboard' && (
            <Dashboard store={store} navigate={navigate} open={setDetail} />
          )}
          {route.page === 'diagnoses' && (
            <Diagnoses
              key={`diagnoses-${route.risk || 'all'}`}
              store={store}
              open={setDetail}
              initialRisk={route.risk}
            />
          )}
          {route.page === 'monitoring' && <Monitoring />}
          {route.page === 'facilities' && (
            <Facilities store={store} open={setDetail} />
          )}
          {route.page === 'users' && <Users store={store} open={setDetail} />}
          {route.page === 'inquiries' && (
            <Inquiries store={store} open={setDetail} />
          )}
        </main>
        <footer className="app-footer">
          <span>SkinCare AI · 관리자 대시보드</span>
          <span>샘플 데이터 · 실제 AI / 의료기관 연동 전</span>
        </footer>
      </div>
      {detail && (
        <Details
          key={`${detail.type}-${'id' in detail ? detail.id : ''}`}
          detail={detail}
          store={store}
          update={setStore}
          open={setDetail}
          close={() => setDetail(null)}
          notify={setToast}
        />
      )}
      {searchOpen && (
        <GlobalSearch
          store={store}
          close={() => setSearchOpen(false)}
          open={(d) => {
            setSearchOpen(false)
            setDetail(d)
          }}
        />
      )}
      {toast && (
        <div className="toast" role="status">
          <span>
            <Check size={16} />
          </span>
          <p>{toast}</p>
          <button aria-label="메시지 닫기" onClick={() => setToast('')}>
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  )
}

function GlobalSearch({
  store,
  close,
  open,
}: {
  store: Store
  close: () => void
  open: (d: Detail) => void
}) {
  const [query, setQuery] = useState('')
  const q = query.trim().toLocaleLowerCase()
  const results: {
    detail: Detail
    title: string
    subtitle: string
    risk?: Risk
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
            detail: { type: 'diagnosis' as const, id: d.id },
            title: d.id,
            subtitle: `${d.userId} · ${d.condition} · ${d.area}`,
            risk: d.risk,
          })),
        ...store.users
          .filter((u) => `${u.id} ${u.name}`.toLocaleLowerCase().includes(q))
          .slice(0, 5)
          .map((u) => ({
            detail: { type: 'user' as const, id: u.id },
            title: u.name,
            subtitle: `사용자 · ${u.id}`,
          })),
        ...store.facilities
          .filter((f) =>
            `${f.name} ${f.region}`.toLocaleLowerCase().includes(q),
          )
          .slice(0, 5)
          .map((f) => ({
            detail: { type: 'facility' as const, id: f.id },
            title: f.name,
            subtitle: `의료기관 · ${f.region} · ${f.type}`,
          })),
        ...store.inquiries
          .filter((i) =>
            `${i.id} ${i.subject} ${i.author}`.toLocaleLowerCase().includes(q),
          )
          .slice(0, 5)
          .map((i) => ({
            detail: { type: 'inquiry' as const, id: i.id },
            title: i.subject,
            subtitle: `문의 #${i.id} · ${i.author}`,
          })),
      ]
    : []
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
                key={`${result.detail.type}-${'id' in result.detail ? result.detail.id : ''}`}
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
        ) : (
          <p className="search-placeholder">검색 결과가 없습니다.</p>
        )}
      </div>
    </Modal>
  )
}
