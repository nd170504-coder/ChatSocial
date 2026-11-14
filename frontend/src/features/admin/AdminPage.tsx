import { useEffect, useId, useRef, useState } from 'react'
import { Navigate } from 'react-router'
import { ApiError } from '../../api/client'
import { Icon } from '../../components/ui/Icon'
import { useAuth } from '../auth/hooks/useAuth'
import { adminApi, type AdminUser, type AdminUsers } from './api'
import './admin.css'

const statuses = {
  ACTIVE: 'Đang hoạt động', SUSPENDED: 'Đã khóa', PENDING_VERIFICATION: 'Chờ xác minh',
  DISABLED: 'Đã vô hiệu hóa', DELETED: 'Đã xóa',
}
const date = (value: string) => new Date(value).toLocaleString('vi-VN')
const errorMessage = (error: unknown) => error instanceof ApiError
  ? error.status === 403 ? 'Bạn không có quyền quản trị. Vui lòng đăng nhập lại nếu quyền vừa thay đổi.'
    : error.status === 400 || error.status === 409 || error.status === 404 ? error.message
      : 'Chưa thể thực hiện thao tác. Vui lòng thử lại.'
  : 'Không kết nối được máy chủ. Vui lòng thử lại.'

export function AdminPage() {
  const { session } = useAuth()
  return session?.user.role === 'ADMIN' ? <AdminDashboard /> : <Navigate to="/feed" replace />
}

function AdminDashboard() {
  const [request, setRequest] = useState({ query: '', page: 0, attempt: 0 })
  const [data, setData] = useState<AdminUsers | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [selected, setSelected] = useState<AdminUser | null>(null)

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true); setError('')
    void adminApi.list(request.query, request.page, controller.signal).then(response => {
      if (active) setData(response.data)
    }).catch(error => { if (active) setError(errorMessage(error)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [request])

  return <main className="admin-page">
    <header className="admin-heading"><div><span className="eyebrow">QUẢN TRỊ CHATSOCIAL</span><h1>Quản lý người dùng</h1><p>Theo dõi tài khoản và quản lý quyền truy cập.</p></div>
      <button type="button" className="button button-secondary" disabled={loading} onClick={() => setRequest(value => ({ ...value, attempt: value.attempt + 1 }))}>Làm mới</button>
    </header>
    <section className="admin-total" aria-label="Thống kê người dùng"><span className="admin-total-icon"><Icon name="user" width="28" height="28" /></span>
      <div><span>Tổng người dùng</span><strong>{data ? data.totalUsers.toLocaleString('vi-VN') : '—'}</strong><p>Chỉ tài khoản USER, gồm cả tài khoản đã khóa và đã xóa.</p></div>
    </section>
    <section className="admin-users" aria-labelledby="admin-list-title">
      <div className="admin-list-heading"><div><h2 id="admin-list-title">{request.query ? 'Kết quả tìm kiếm' : 'Tài khoản mới nhất'}</h2><p>Sắp xếp theo ngày tham gia, mới nhất trước.</p></div>
        <form className="admin-search" role="search" onSubmit={event => {
          event.preventDefault()
          const query = String(new FormData(event.currentTarget).get('query') || '').trim()
          setNotice(''); setRequest({ query, page: 0, attempt: request.attempt + 1 })
        }}><label className="admin-search-field"><Icon name="search" /><input aria-label="Tìm tài khoản theo username hoặc email" name="query" type="search" maxLength={255} placeholder="Username hoặc email…" /></label>
          <button type="submit" className="button button-primary">Tìm kiếm</button></form>
      </div>
      {notice && <p className="admin-notice" role="status">{notice}</p>}
      {error && <p className="admin-error" role="alert">{error} <button type="button" onClick={() => setRequest(value => ({ ...value, attempt: value.attempt + 1 }))}>Thử lại</button></p>}
      {loading && <p className="admin-state" role="status">Đang tải danh sách tài khoản…</p>}
      {!loading && !error && data && <>
        <div className="admin-table-scroll"><table className="admin-table"><caption className="admin-table-caption">{data.totalMatches.toLocaleString('vi-VN')} tài khoản{request.query ? ` khớp với “${request.query}”` : ''}</caption>
          <thead><tr><th scope="col">Tài khoản</th><th scope="col">Trạng thái</th><th scope="col">Ngày tham gia</th><th scope="col">Lý do cập nhật gần nhất</th><th scope="col">Thao tác</th></tr></thead>
          <tbody>{data.items.map(user => <tr key={user.id}>
            <td><strong>{user.username}</strong><span className="admin-email">{user.email}</span></td>
            <td><span className={`admin-status admin-status-${user.status.toLowerCase()}`}>{statuses[user.status]}</span></td>
            <td className="admin-date">{date(user.createdAt)}</td>
            <td className="admin-reason">{user.moderationReason || '—'}{user.moderatedAt && <small>{date(user.moderatedAt)}</small>}</td>
            <td>{user.role !== 'ADMIN' && ['ACTIVE', 'SUSPENDED'].includes(user.status)
              ? <button className={`admin-action ${user.status === 'ACTIVE' ? 'admin-action-lock' : ''}`} type="button" onClick={() => { setNotice(''); setSelected(user) }}>{user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button>
              : <span className="admin-muted">—</span>}</td>
          </tr>)}</tbody>
        </table></div>
        {data.items.length === 0 && <p className="admin-state">Không tìm thấy tài khoản phù hợp.</p>}
        <nav className="admin-pagination" aria-label="Phân trang tài khoản"><span>Trang {data.page + 1}</span><div>
          <button className="button button-secondary" type="button" disabled={request.page === 0} onClick={() => setRequest(value => ({ ...value, page: value.page - 1 }))}>Trang trước</button>
          <button className="button button-secondary" type="button" disabled={!data.hasNext} onClick={() => setRequest(value => ({ ...value, page: value.page + 1 }))}>Trang sau</button>
        </div></nav>
      </>}
    </section>
    {selected && <StatusDialog user={selected} onDismiss={() => setSelected(null)} onSaved={user => {
      setSelected(null); setNotice(`Đã ${user.status === 'SUSPENDED' ? 'khóa' : 'mở khóa'} tài khoản ${user.username}.`)
      setRequest(value => ({ ...value, attempt: value.attempt + 1 }))
    }} />}
  </main>
}

function StatusDialog({ user, onDismiss, onSaved }: { user: AdminUser; onDismiss: () => void; onSaved: (user: AdminUser) => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const submitting = useRef(false)
  const titleId = useId()
  const descriptionId = useId()
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const locking = user.status === 'ACTIVE'
  const title = locking ? 'Khóa tài khoản' : 'Mở khóa tài khoản'

  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close() }, [])

  return <dialog ref={dialog} className="admin-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); if (!submitting.current) onDismiss() }}>
    <span className="admin-dialog-icon"><Icon name="lock" width="26" height="26" /></span><h2 id={titleId}>{title}</h2>
    <p id={descriptionId}><strong>{user.username}</strong> ({user.email})<br />{locking ? 'Tài khoản sẽ mất quyền truy cập và các phiên đăng nhập sẽ bị thu hồi.' : 'Tài khoản có thể đăng nhập trở lại. Các phiên đăng nhập cũ vẫn bị thu hồi.'}</p>
    <form onSubmit={event => {
      event.preventDefault()
      if (submitting.current || !reason.trim()) return
      submitting.current = true; setBusy(true); setError('')
      void adminApi.changeStatus(user.id, locking, reason.trim()).then(response => onSaved(response.data))
        .catch(error => setError(errorMessage(error)))
        .finally(() => { submitting.current = false; setBusy(false) })
    }}><label className="admin-reason-field">Lý do {locking ? 'khóa' : 'mở khóa'}<textarea autoFocus required maxLength={500} rows={4} value={reason} disabled={busy} onChange={event => setReason(event.target.value)} placeholder="Nhập lý do để lưu cùng trạng thái tài khoản…" /></label>
      <small className="admin-character-count">{reason.length}/500 ký tự</small>
      {error && <p className="admin-error" role="alert">{error}</p>}
      <div className="admin-dialog-actions"><button type="button" className="button button-secondary" disabled={busy} onClick={onDismiss}>Hủy</button><button type="submit" className={`button ${locking ? 'admin-confirm-lock' : 'button-primary'}`} disabled={busy || !reason.trim()}>{busy ? 'Đang lưu…' : title}</button></div>
    </form>
  </dialog>
}
