import { NavLink, Outlet } from 'react-router'
import { Icon } from '../components/ui/Icon'
import { AccountDropdown } from '../components/AccountDropdown'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useProfile } from '../features/profile/hooks/useProfile'

export function AppLayout() {
  const { session } = useAuth()
  const profileState = useProfile(session!.user.id)
  return <div className="app-shell">
    <aside className="app-sidebar">
      <NavLink className="brand" to="/feed"><img src="/chatsocial.svg" alt="" width="40" height="40" />ChatSocial<span className="brand-dot">.</span></NavLink>
      <div className="sidebar-section-label">BẠN BÈ</div>
      <nav className="friends-navigation" aria-label="Bạn bè"><NavLink className="sidebar-link" to="/friends/search"><Icon name="search" />Tìm bạn</NavLink></nav>
    </aside>
    <div className="app-workspace">
      <header className="workspace-header"><nav className="workspace-nav" aria-label="Điều hướng chính"><NavLink to="/feed"><Icon name="shield" />Công khai</NavLink><NavLink to="/profile"><Icon name="user" />Hồ sơ</NavLink></nav><AccountDropdown avatarUrl={profileState.profile?.avatarUrl} /></header>
      <Outlet context={profileState} />
    </div>
  </div>
}
