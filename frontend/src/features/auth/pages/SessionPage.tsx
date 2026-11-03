import { useNavigate } from 'react-router'
import { useAuth } from '../hooks/useAuth'
import { useAuthForm } from '../hooks/useAuthForm'
import { authStore } from '../session'
import { FormFeedback } from '../components/FormFeedback'

// Landing page for the Auth feature. Profile/account screens follow separately.
export function SessionPage() {
  const { session } = useAuth()
  const form = useAuthForm()
  const navigate = useNavigate()
  if (!session) return null
  const user = session.user
  return <div className="signed-in-layout">
    <header className="app-header"><a className="brand" href="/"><img src="/chatsocial.svg" alt="" width="40" height="40" />ChatSocial<span className="brand-dot">.</span></a><span className="session-badge"><span className="status-dot" /> Đã đăng nhập</span></header>
    <main className="welcome-card">
      <span className="welcome-avatar" aria-hidden="true">{user.fullName.slice(0, 1).toUpperCase()}</span>
      <span className="eyebrow form-eyebrow">RẤT VUI ĐƯỢC GẶP BẠN</span>
      <h1>Xin chào, {user.fullName}!</h1>
      <p className="form-description">Bạn đã đăng nhập vào ChatSocial.</p>
      <dl className="user-details"><div><dt>Tên người dùng</dt><dd>@{user.username}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div></dl>
      <FormFeedback error={form.error} />
      <button className="button button-secondary" disabled={form.busy} onClick={() => void form.run(async () => {
        await authStore.logout()
        navigate('/login', { replace: true, state: { notice: 'Bạn đã đăng xuất an toàn.' } })
      })}>{form.busy ? 'Đang đăng xuất…' : 'Đăng xuất'}<span aria-hidden="true">↗</span></button>
    </main>
  </div>
}
