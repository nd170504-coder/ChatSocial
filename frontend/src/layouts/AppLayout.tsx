import { NavLink, Outlet, useLocation } from 'react-router'
import { AccountDropdown } from '../components/AccountDropdown'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useProfile } from '../features/profile/hooks/useProfile'

export function AppLayout() {
  const { session } = useAuth()
  const profileState = useProfile(session!.user.id)
  const location = useLocation()
  const pageTitle = location.pathname.startsWith('/settings/') ? 'Cài đặt tài khoản' : 'Hồ sơ của tôi'
  return <div className="app-shell">
    <aside className="app-sidebar">
      <NavLink className="brand" to="/profile"><img src="/chatsocial.svg" alt="" width="40" height="40" />ChatSocial<span className="brand-dot">.</span></NavLink>
      <div className="sidebar-section-label">BẠN BÈ</div>
    </aside>
    <div className="app-workspace">
      <header className="workspace-header"><span>Không gian cá nhân <span aria-hidden="true">/</span> <strong>{pageTitle}</strong></span><AccountDropdown avatarUrl={profileState.profile?.avatarUrl} /></header>
      <Outlet context={profileState} />
    </div>
  </div>
}
