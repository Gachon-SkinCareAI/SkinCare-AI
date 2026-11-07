import Sidebar from './layouts/Sidebar'
import { useEffect, useState } from 'react'
import { Bell, Check, ChevronRight, Menu, Search, X } from 'lucide-react'
import GlobalSearch from './components/GlobalSearch'
import { pages, getRoute } from './constants/navigation'
import { usePreviewStore } from './hooks/usePreviewStore'
import {
  type Detail,
  type Navigate,
} from './data'
import Dashboard from './pages/Dashboard'
import Diagnoses from './pages/Diagnoses'
import Monitoring from './pages/Monitoring'
import Facilities from './pages/Facilities'
import Users from './pages/Users'
import Inquiries from './pages/Inquiries'
import Details from './Details'

export default function App() {
  const { store, setStore, storageError } = usePreviewStore()
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
    window.addEventListener('hashchange', sync)
    return () => window.removeEventListener('hashchange', sync)
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

