import Sidebar from './layouts/Sidebar'
import { useEffect, useState } from 'react'
import { Bell, Check, ChevronRight, Menu, Search, X } from 'lucide-react'
import GlobalSearch from './components/GlobalSearch'
import { ADMIN_BASE_PATH, pages, getRoute } from './constants/navigation'
import { usePreviewStore } from './hooks/usePreviewStore'
import {
  type Detail,
  type Navigate,
} from './data'
import { hasSession, logout } from './services/api'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Diagnoses from './pages/Diagnoses'
import Monitoring from './pages/Monitoring'
import Facilities from './pages/Facilities'
import Users from './pages/Users'
import Inquiries from './pages/Inquiries'
import Details from './Details'

export default function App() {
  const [authenticated, setAuthenticated] = useState(hasSession)
  const { store, setStore, storageError, loading, error, reload } = usePreviewStore(authenticated)
  const [route, setRoute] = useState(getRoute)
  const [collapsed, setCollapsed] = useState(false)
  const [mobileMenu, setMobileMenu] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [notifications, setNotifications] = useState(false)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [toast, setToast] = useState('')
  const active = pages.find((p) => p.id === route.page)!
  useEffect(() => {
    const sync = () => {
      setRoute(getRoute())
      setMobileMenu(false)
      setNotifications(false)
      setDetail(null)
    }
    window.addEventListener('popstate', sync)
    return () => window.removeEventListener('popstate', sync)
  }, [])

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
    const query = risk ? `?risk=${encodeURIComponent(risk)}` : ''
    window.history.pushState({}, '', `${ADMIN_BASE_PATH}/${page}${query}`)
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
  if (!authenticated) return <Login onSuccess={() => setAuthenticated(true)} />
  if (error) return <main className="loading-state"><p role="alert">{error}</p><button className="button primary" onClick={() => void reload()}>다시 시도</button></main>
  if (loading || !store) return <main className="loading-state">관리자 데이터를 불러오는 중입니다</main>
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
      <Sidebar mobileMenu={mobileMenu} collapsed={collapsed} toggleSidebar={toggleSidebar} navigate={navigate} pageId={route.page} />
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
            <button className="demo-chip" onClick={() => { void logout(); setAuthenticated(false) }}>로그아웃</button>
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
                    답변을 기다리는 문의가 {pending.length}건 있습니다.
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
                      모든 문의에 답변했습니다.
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          {storageError && <div role="alert" className="storage-warning">API 데이터를 갱신하지 못했습니다</div>}
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
          {route.page === 'monitoring' && <Monitoring store={store} />}
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
          <span>백엔드 API 연동</span>
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
