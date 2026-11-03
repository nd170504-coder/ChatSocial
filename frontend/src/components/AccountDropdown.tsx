import { useEffect, useId, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Icon } from './ui/Icon'
import { Avatar } from '../features/profile/components/Avatar'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useAuthForm } from '../features/auth/hooks/useAuthForm'
import { authStore } from '../features/auth/session'

export function AccountDropdown({ avatarUrl }: { avatarUrl?: string | null }) {
  const { session } = useAuth()
  const form = useAuthForm()
  const navigate = useNavigate()
  const location = useLocation()
  const [openAt, setOpenAt] = useState<string | null>(null)
  const open = openAt === location.key
  const wrapper = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const panelId = useId()

  useEffect(() => {
    if (!open) return
    panel.current?.querySelector<HTMLAnchorElement>('a')?.focus()
    function dismiss(event: PointerEvent) {
      if (event.target instanceof Node && !wrapper.current?.contains(event.target)) setOpenAt(null)
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpenAt(null)
        trigger.current?.focus()
      }
    }
    document.addEventListener('pointerdown', dismiss)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', dismiss)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  if (!session) return null
  const user = session.user
  return <div className="account-dropdown" ref={wrapper} onBlur={event => {
    if (event.relatedTarget instanceof Node && !event.currentTarget.contains(event.relatedTarget)) setOpenAt(null)
  }}>
    <button ref={trigger} className="account-trigger" type="button" aria-label="Mở tùy chọn tài khoản"
      aria-expanded={open} aria-controls={panelId} onClick={() => setOpenAt(open ? null : location.key)}>
      <Avatar name={user.fullName} src={avatarUrl} className="account-avatar" />
      <span className="account-trigger-chevron" aria-hidden="true">⌄</span>
    </button>
    {open && <div ref={panel} id={panelId} className="account-panel">
      <div className="account-panel-identity"><Avatar name={user.fullName} src={avatarUrl} className="account-avatar" /><div><strong>{user.fullName}</strong><span>@{user.username}</span></div></div>
      <p className="account-status"><span className="status-dot" aria-hidden="true" />Đã đăng nhập</p>
      <nav aria-label="Tùy chọn tài khoản">
        <Link className="account-action" to="/profile" onClick={() => { setOpenAt(null); trigger.current?.focus() }}><Icon name="user" />Hồ sơ của tôi</Link>
        <Link className="account-action" to="/settings/account" onClick={() => { setOpenAt(null); trigger.current?.focus() }}><Icon name="settings" />Cài đặt tài khoản</Link>
      </nav>
      {form.error && <p className="account-error" role="alert">{form.error}</p>}
      <button className="account-action account-logout" type="button" disabled={form.busy} onClick={() => void form.run(async () => {
        await authStore.logout()
        navigate('/login', { replace: true, state: { notice: 'Bạn đã đăng xuất an toàn.' } })
      })}><Icon name="logout" />{form.busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    </div>}
  </div>
}
