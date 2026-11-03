import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../hooks/useAuth'
import { authStore } from '../session'

export function AuthGate({ requireSession = false }: { requireSession?: boolean }) {
  const auth = useAuth()
  if (auth.status === 'loading') return <main className="session-state" role="status"><img src="/chatsocial.svg" alt="" width="56" height="56" /><p>Đang kiểm tra phiên đăng nhập…</p></main>
  if (auth.status === 'error') return <main className="session-state"><h1>Chưa thể kết nối</h1><p>Chúng mình chưa thể kiểm tra phiên đăng nhập. Hãy kiểm tra kết nối và thử lại.</p><button className="button button-primary" onClick={() => void authStore.initialize()}>Thử lại</button></main>
  if (requireSession && auth.status !== 'authenticated') return <Navigate to="/login" replace />
  if (!requireSession && auth.status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}
