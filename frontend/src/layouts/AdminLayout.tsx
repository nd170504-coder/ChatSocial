import { Navigate, NavLink, Outlet, useNavigate } from 'react-router'
import { Icon } from '../components/ui/Icon'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useAuthForm } from '../features/auth/hooks/useAuthForm'
import { authStore } from '../features/auth/session'
import '../features/admin/admin.css'

export function AdminLayout() {
  const { session } = useAuth()
  const form = useAuthForm()
  const navigate = useNavigate()
  if (session?.user.role !== 'ADMIN') return <Navigate to="/feed" replace />

  return <div className="app-shell admin-shell">
    <aside className="app-sidebar admin-sidebar">
      <NavLink className="brand" to="/admin"><img src="/chatsocial.svg" alt="" width="40" height="40" />ChatSocial<span className="brand-dot">.</span></NavLink>
      <div className="sidebar-section-label">KHÔNG GIAN QUẢN TRỊ</div>
      <nav className="admin-navigation" aria-label="Điều hướng quản trị">
        <NavLink className="sidebar-link" to="/admin" end><Icon name="statistics" />Tổng quan</NavLink>
        <NavLink className="sidebar-link" to="/admin/users"><Icon name="user" />Quản trị người dùng</NavLink>
      </nav>
      <div className="admin-session"><span className="admin-session-badge"><Icon name="lock" />Quản trị viên</span><strong>{session.user.fullName}</strong><span className="admin-session-email">{session.user.email}</span>
        {form.error && <p className="admin-error" role="alert">{form.error}</p>}
        <button className="admin-logout" type="button" disabled={form.busy} onClick={() => void form.run(async () => {
          await authStore.logout(); navigate('/login', { replace: true })
        })}><Icon name="logout" />{form.busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      </div>
    </aside>
    <div className="app-workspace"><Outlet /></div>
  </div>
}
