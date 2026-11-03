import { Link, Outlet, useLocation } from 'react-router'

export function AuthLayout() {
  const { pathname } = useLocation()
  return (
    <div className="auth-layout">
      <aside className="brand-panel" aria-label="Giới thiệu ChatSocial">
        <Link className="brand" to="/login"><img src="/chatsocial.svg" alt="" width="42" height="42" />ChatSocial<span className="brand-dot">.</span></Link>
        <div className="brand-story">
          <span className="eyebrow"><span /> MỖI CUỘC TRÒ CHUYỆN, MỘT KẾT NỐI</span>
          <h1>Gần nhau hơn.<br />Bắt đầu từ một<br /><em>lời chào.</em></h1>
          <p>Một nơi dành cho những câu chuyện đời thường,<br className="desktop-break" /> những người bạn và những kết nối mới.</p>
          <div className="conversation-art" aria-hidden="true">
            <div className="art-orbit" />
            <div className="chat-note note-one"><span className="art-avatar avatar-purple">L</span><div><b>Linh</b><p>Cuối tuần này cà phê nha? ☕</p></div><span className="note-time">14:20</span></div>
            <div className="chat-note note-two"><span className="art-avatar avatar-blue">M</span><div><b>Minh</b><p>Có mặt! Có chuyện muốn kể nè ✨</p></div></div>
            <div className="art-status"><span className="status-dot" /> Một lời chào, nhiều câu chuyện.</div>
            <span className="art-spark spark-one">✳</span><span className="art-spark spark-two">✦</span>
          </div>
        </div>
        <footer className="brand-footer"><span>Kết nối theo cách của bạn.</span><span>© {new Date().getFullYear()} ChatSocial</span></footer>
      </aside>
      <div className="auth-panel">
        <header className="auth-topbar"><span>{pathname === '/register' ? 'Đã có tài khoản?' : 'Bạn mới đến đây?'}</span><Link to={pathname === '/register' ? '/login' : '/register'}>{pathname === '/register' ? 'Đăng nhập' : 'Tạo tài khoản'} <span aria-hidden="true">↗</span></Link></header>
        <main className="auth-content"><Outlet /></main>
        <footer className="auth-footer">Một không gian cho những kết nối ý nghĩa.</footer>
      </div>
    </div>
  )
}
