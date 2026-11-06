import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../../../api/client'
import { Icon } from '../../../components/ui/Icon'
import { Avatar } from '../../profile/components/Avatar'
import { friendRequestApi } from '../api'
import type { FriendRequestDirection, FriendRequestPage } from '../types'
import { friendRequestError, searchError } from '../utils'
import '../friends.css'

function RequestList({ direction, page, onPage, onChanged }: {
  direction: FriendRequestDirection; page: number
  onPage: (page: number) => void; onChanged: (message: string) => void
}) {
  const [data, setData] = useState<FriendRequestPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const locked = useRef(false)
  const active = useRef(true)
  useEffect(() => {
    const controller = new AbortController()
    active.current = true
    let current = true
    void friendRequestApi.list(direction, page, controller.signal).then(response => {
      if (current) setData(response.data)
    }).catch(cause => {
      if (current) setError(cause instanceof ApiError && cause.status >= 500
        ? 'Chưa thể tải danh sách lời mời. Vui lòng thử lại.' : searchError(cause))
    })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false; active.current = false; controller.abort() }
  }, [direction, page, attempt])

  async function cancel(id: string) {
    if (locked.current) return
    locked.current = true; setCancellingId(id); setError('')
    try {
      await friendRequestApi.cancel(id)
      if (active.current) onChanged('Đã hủy lời mời kết bạn.')
    } catch (cause) {
      if (!active.current) return
      if (cause instanceof ApiError && [404, 409].includes(cause.status)) onChanged('Lời mời đã thay đổi. Danh sách đã được cập nhật.')
      else setError(friendRequestError(cause, 'cancel'))
    } finally {
      locked.current = false
      if (active.current) setCancellingId(null)
    }
  }

  return <section aria-label={direction === 'incoming' ? 'Lời mời đã nhận' : 'Lời mời đã gửi'} aria-busy={loading}>
    {error && <div className="feedback feedback-error" role="alert">{error}<button className="friend-search-retry" type="button" disabled={!!cancellingId} onClick={() => { setError(''); setLoading(true); setAttempt(value => value + 1) }}>Thử lại</button></div>}
    {loading && <p className="friend-search-state" role="status">Đang tải lời mời…</p>}
    {!loading && data && <>
      {data.items.length > 0 ? <ul className="friend-request-list">{data.items.map(item => <li key={item.id} className="friend-request-card">
        <Link className="friend-request-person" to={`/users/${encodeURIComponent(item.user.id)}`} aria-label={`Xem hồ sơ của ${item.user.fullName}`}><Avatar name={item.user.fullName} src={item.user.avatarUrl} className="friend-request-avatar" /><div><h2>{item.user.fullName}</h2><p>@{item.user.username}</p><time dateTime={item.sentAt}>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.sentAt))}</time></div></Link>
        <div className="friend-request-actions">{direction === 'outgoing'
          ? <button type="button" className="button button-secondary" disabled={!!cancellingId} onClick={() => void cancel(item.id)}>{cancellingId === item.id ? 'Đang hủy…' : 'Hủy lời mời'}</button>
          : <><span>Đang chờ bạn phản hồi</span><Link className="button button-secondary" to={`/users/${encodeURIComponent(item.user.id)}`}>Xem hồ sơ</Link></>}</div>
      </li>)}</ul> : <div className="friend-search-empty friend-search-welcome"><Icon name="mail" width="36" height="36" /><h2>{direction === 'incoming' ? 'Chưa có lời mời mới' : 'Chưa có lời mời đang chờ'}</h2><p>{direction === 'incoming' ? 'Lời mời kết bạn từ mọi người sẽ xuất hiện tại đây.' : 'Tìm người bạn biết và gửi lời mời từ hồ sơ của họ.'}</p><Link className="friend-request-find" to="/friends/search">Tìm bạn →</Link></div>}
      {(data.page > 0 || data.hasNext) && <nav className="friend-pagination" aria-label="Phân trang lời mời"><button className="button button-secondary" type="button" disabled={!!cancellingId || data.page === 0} onClick={() => onPage(data.page - 1)}>Trang trước</button><span>Trang {data.page + 1}</span><button className="button button-secondary" type="button" disabled={!!cancellingId || !data.hasNext} onClick={() => onPage(data.page + 1)}>Trang sau</button></nav>}
    </>}
  </section>
}

export function FriendRequestsPage() {
  const [params, setParams] = useSearchParams()
  const direction: FriendRequestDirection = params.get('tab') === 'outgoing' ? 'outgoing' : 'incoming'
  const rawPage = params.get('page') || '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState('')
  return <main className="profile-content friend-requests-page">
    <div className="profile-page-heading"><div><span className="eyebrow form-eyebrow">NHỮNG KẾT NỐI ĐANG CHỜ</span><h1>Lời mời kết bạn</h1><p>Theo dõi những lời mời bạn đã nhận và đã gửi.</p></div><button type="button" className="visitor-refresh" onClick={() => { setNotice(''); setRevision(value => value + 1) }}>Làm mới</button></div>
    <nav className="friend-request-tabs" aria-label="Loại lời mời"><Link to="?tab=incoming" aria-current={direction === 'incoming' ? 'page' : undefined} onClick={() => setNotice('')}><Icon name="mail" />Đã nhận</Link><Link to="?tab=outgoing" aria-current={direction === 'outgoing' ? 'page' : undefined} onClick={() => setNotice('')}><Icon name="arrow" />Đã gửi</Link></nav>
    {notice && <div className="feedback feedback-success" role="status">{notice}</div>}
    <RequestList key={`${direction}:${page}:${revision}`} direction={direction} page={page}
      onPage={next => setParams({ tab: direction, page: String(next) })}
      onChanged={message => { setNotice(message); setParams({ tab: direction }); setRevision(value => value + 1) }} />
  </main>
}
