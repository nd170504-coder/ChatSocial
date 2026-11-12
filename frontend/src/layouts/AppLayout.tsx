import { NavLink, Outlet } from 'react-router'
import { Icon } from '../components/ui/Icon'
import { AccountDropdown } from '../components/AccountDropdown'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useProfile } from '../features/profile/hooks/useProfile'
import { NotificationBell } from '../features/notifications/NotificationBell'
import { ChatProvider, useChat } from '../features/chat/ChatProvider'
import { ChatNavLink } from '../features/chat/ChatNavLink'
import { PresenceProvider } from '../features/presence/PresenceProvider'

export function AppLayout() {
  const { session } = useAuth()
  const profileState = useProfile(session!.user.id)
  return <PresenceProvider key={session!.user.id} userId={session!.user.id}><ChatProvider userId={session!.user.id}><AppContent userId={session!.user.id} profileState={profileState} /></ChatProvider></PresenceProvider>
}

function AppContent({ userId, profileState }: { userId: string; profileState: ReturnType<typeof useProfile> }) {
  const { relationshipRevision } = useChat()
  return <div key={relationshipRevision} className="app-shell">
    <aside className="app-sidebar">
      <NavLink className="brand" to="/feed"><img src="/chatsocial.svg" alt="" width="40" height="40" />ChatSocial<span className="brand-dot">.</span></NavLink>
      <div className="sidebar-section-label">KẾT NỐI</div>
      <nav className="friends-navigation" aria-label="Điều hướng chính"><NavLink className="sidebar-link" to="/feed"><Icon name="feed" />Bảng tin</NavLink><NavLink className="sidebar-link" to="/friends" end><Icon name="user" />Bạn bè</NavLink><NavLink className="sidebar-link" to="/friends/search"><Icon name="search" />Tìm bạn</NavLink><NavLink className="sidebar-link" to="/friends/requests"><Icon name="mail" />Lời mời</NavLink></nav>
      <ChatNavLink />
      <NotificationBell key={userId} userId={userId} />
      <div className="sidebar-account"><AccountDropdown avatarUrl={profileState.profile?.avatarUrl} fullName={profileState.profile?.fullName} /></div>
    </aside>
    <div className="app-workspace">
      <Outlet context={profileState} />
    </div>
  </div>
}
