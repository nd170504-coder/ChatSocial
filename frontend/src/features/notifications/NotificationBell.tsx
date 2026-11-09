import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { Icon } from '../../components/ui/Icon'
import { Avatar } from '../profile/components/Avatar'
import { postTime } from '../posts/utils'
import { notificationsApi } from './api'
import type { NotificationItem } from './types'
import { useNotifications } from './useNotifications'
import { notificationDescription, notificationTarget, notificationText } from './utils'
import './notifications.css'

export function NotificationBell({ userId }: { userId: string }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const state = useNotifications(userId, open)
  const navigate = useNavigate()
  const dialog = useRef<HTMLDialogElement>(null)
  const active = useRef(false)
  const locked = useRef(false)
  const titleId = useId()
  const panelId = useId()

  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  useEffect(() => {
    if (!open) return
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    return () => { element?.close(); document.body.style.overflow = previousOverflow }
  }, [open])

  function close() {
    if (locked.current) return
    dialog.current?.close(); setOpen(false)
  }

  async function read(item?: NotificationItem) {
    if (locked.current) return
    locked.current = true; setBusy(true); setError('')
    try {
      if (!item) await notificationsApi.readAll()
      else if (item.readAt === null) await notificationsApi.read(item.id)
      if (!active.current) return
      state.refresh()
      if (item) {
        dialog.current?.close(); setOpen(false)
        navigate(notificationTarget(item))
      }
    } catch (cause) {
      if (active.current) {
        setError(cause instanceof ApiError && cause.status === 404
          ? 'Thông báo không còn khả dụng. Hãy làm mới danh sách.'
          : 'Chưa thể đánh dấu đã đọc. Vui lòng thử lại.')
        state.refresh()
      }
    } finally {
      locked.current = false
      if (active.current) setBusy(false)
    }
  }

  const label = state.countError ? 'Thông báo, chưa tải được số chưa đọc'
    : state.unread === null ? 'Thông báo, đang tải' : `Thông báo, ${state.unread} chưa đọc`
  return <div className="notifications-widget">
    <button type="button" className="sidebar-link notification-trigger" aria-label={label} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? panelId : undefined} onClick={() => { setError(''); state.refresh(); setOpen(true) }}>
      <Icon name="bell" /><span>Thông báo</span>
      {(state.countError || state.unread === null || state.unread > 0) && <span className="notification-badge" aria-hidden="true">{state.countError ? '!' : state.unread === null ? '…' : state.unread > 99 ? '99+' : state.unread}</span>}
    </button>
    <span className="notification-announcement" role="status">{state.latest ? `Thông báo mới: ${notificationText(state.latest)}` : ''}</span>
    {open && createPortal(<dialog ref={dialog} id={panelId} className="notifications-dialog" aria-labelledby={titleId}
      onCancel={event => { event.preventDefault(); close() }} onClick={event => { if (event.target === event.currentTarget) close() }}>
      <header className="notifications-heading"><div><h2 id={titleId}>Thông báo</h2><span className={`notification-connection ${state.status}`}><i />{state.status === 'connected' ? 'Đang nhận thông báo trực tiếp' : state.status === 'connecting' ? 'Đang kết nối…' : 'Đang kết nối lại…'}</span></div><button type="button" className="icon-button" aria-label="Đóng thông báo" disabled={busy} onClick={close}><Icon name="close" /></button></header>
      <div className="notifications-toolbar"><span>{state.countError ? 'Chưa tải được số chưa đọc' : state.unread === null ? 'Đang tải…' : `${state.unread.toLocaleString('vi-VN')} chưa đọc`}</span><button type="button" disabled={busy || !state.unread || state.countError} onClick={() => void read()}>Đọc tất cả</button><button type="button" disabled={busy} onClick={state.refresh}>Làm mới</button></div>
      {error && <p className="notification-error" role="alert">{error}</p>}
      {state.countError && <p className="notification-error" role="alert">Chưa tải được số thông báo chưa đọc. <button type="button" onClick={state.refresh}>Thử lại</button></p>}
      <div className="notifications-scroll" aria-busy={state.loading}>
        <ul className="notification-list">{state.data?.items.map(item => <li key={item.id}>
          <button className={`notification-item${item.readAt === null ? ' unread' : ''}`} type="button" disabled={busy} onClick={() => void read(item)}>
            <Avatar userId={item.actorId} name={item.actorName} src={item.actorAvatarUrl} className="notification-avatar" />
            <span className="notification-item-body"><span><strong>{item.actorName}</strong> {notificationDescription(item)}</span><time dateTime={item.createdAt}>{postTime(item.createdAt)}</time><span className="notification-read-label">{item.readAt === null ? 'Chưa đọc' : 'Đã đọc'}</span></span>
            {item.readAt === null && <span className="notification-unread-dot" aria-hidden="true" />}
          </button>
        </li>)}</ul>
        {state.loading && <p className="notification-state" role="status">Đang tải thông báo…</p>}
        {state.listError && <div className="notification-error" role="alert">Chưa thể tải danh sách thông báo. <button type="button" disabled={busy || state.loading} onClick={state.retryList}>Thử tải lại</button></div>}
        {!state.loading && !state.listError && state.data?.items.length === 0 && <div className="notification-empty"><Icon name="bell" width="36" height="36" /><h3>Bạn chưa có thông báo</h3><p>Lời mời kết bạn và tương tác mới sẽ xuất hiện tại đây.</p></div>}
        {state.data?.hasNext && !state.listError && <button className="notifications-more" type="button" disabled={busy || state.loading} onClick={state.loadMore}>Xem thêm thông báo</button>}
      </div>
    </dialog>, document.body)}
  </div>
}
