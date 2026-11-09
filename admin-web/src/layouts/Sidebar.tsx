import { Menu } from 'lucide-react'
import { ADMIN_BASE_PATH, pages } from '../constants/navigation'
import type { Navigate, Page } from '../data'

export default function Sidebar({ mobileMenu, collapsed, toggleSidebar, navigate, pageId }: {
  mobileMenu: boolean; collapsed: boolean; toggleSidebar: () => void; navigate: Navigate; pageId: Page
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
              src="public/123.png"
              alt=""
              width="45"
              height="45"
            />
            <span className="brand-text">
              <a>SkinCare AI</a>
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
  )
}
