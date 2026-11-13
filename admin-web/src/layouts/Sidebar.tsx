import { LogOut, Menu } from 'lucide-react'
import { ADMIN_BASE_PATH, pages } from '../constants/navigation'
import type { Navigate, Page } from '../data'

export default function Sidebar({ mobileMenu, collapsed, toggleSidebar, navigate, pageId, onLogout, loggingOut, adminName }: {
  mobileMenu: boolean; collapsed: boolean; toggleSidebar: () => void; navigate: Navigate; pageId: Page
  onLogout: () => void; loggingOut: boolean; adminName: string
}) {
  return (
      <aside className={`sidebar ${mobileMenu ? 'mobile-open' : ''}`}>
        <div className="brand-row">
          <a
            className="brand"
            href={`${ADMIN_BASE_PATH}/dashboard`}
            onClick={(e) => {
              e.preventDefault()
              navigate('dashboard')
            }}
            aria-label="SkinCare AI 대시보드"
          >
            <img
              className="brand-mark"
              src={`${import.meta.env.BASE_URL}skincare_main_logo.png`}
              alt="skincare AI"
              width="45"
              height="45"
            />
            <span className="brand-text">
              <strong>SkinCare AI</strong>
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
              className={`nav-item ${page.id === pageId ? 'active' : ''}`}
              aria-current={page.id === pageId ? 'page' : undefined}
              title={page.title}
              href={`${ADMIN_BASE_PATH}/${page.id}`}
              onClick={(e) => {
                e.preventDefault()
                navigate(page.id)
              }}
            >
              <span className="nav-icon" aria-hidden="true">
              </span>
              <span className="nav-label">{page.title}</span>
            </a>
          ))}
        </nav>
        <div className="admin-sidebar-footer">
          <div className="admin-profile">
            <span className="admin-avatar">관</span>
            <div>
              <strong>{adminName}</strong>
              <small>관리자 계정</small>
            </div>
          </div>
          <button
            type="button"
            className="sidebar-logout"
            onClick={onLogout}
            disabled={loggingOut}
            title="로그아웃"
          >
            <LogOut size={17} />
            <span>{loggingOut ? '로그아웃 중...' : '로그아웃'}</span>
          </button>
        </div>
      </aside>
  )
}
