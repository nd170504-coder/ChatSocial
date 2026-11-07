import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../../../api/client'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { Avatar } from '../../profile/components/Avatar'
import { friendsApi } from '../api'
import { FriendCount } from '../components/FriendCount'
import type { FriendPage } from '../types'
import '../friends.css'

function FriendsList({ page, onPage }: { page: number; onPage: (page: number) => void }) {
  const [data, setData] = useState<FriendPage | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void friendsApi.list(page, controller.signal).then(response => {
      if (active) setData(response.data)
    }).catch(cause => {
      if (active) setError(cause instanceof ApiError && cause.status === 401
        ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : 'Chưa thể tải danh sách bạn bè. Vui lòng thử lại.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [page, attempt])

  return <section aria-label="Danh sách bạn bè" aria-busy={loading}>
    {loading && <p className="friend-search-state" role="status">Đang tải bạn bè…</p>}
    {error && <div className="feedback feedback-error" role="alert">{error}<button className="friend-search-retry" type="button" onClick={() => { setError(''); setLoading(true); setAttempt(value => value + 1) }}>Thử lại</button></div>}
    {!loading && !error && data && <>
      {data.items.length > 0 ? <ul className="friend-result-grid">{data.items.map(friend => <li key={friend.userId}><Link className="friend-result-card friend-profile-link" to={`/users/${encodeURIComponent(friend.userId)}`} aria-label={`Xem hồ sơ của ${friend.fullName}`}><Avatar name={friend.fullName} src={friend.avatarUrl} className="friend-result-avatar" /><div><h2 className="friend-list-name">{friend.fullName}</h2><p>@{friend.username}</p></div><Icon className="friend-list-arrow" name="arrow" /></Link></li>)}</ul>
        : <div className="friend-search-empty friend-search-welcome"><Icon name="user" width="36" height="36" /><h2>{page === 0 ? 'Bạn chưa có bạn bè' : 'Trang này chưa có kết quả'}</h2><p>{page === 0 ? 'Những người đã chấp nhận kết bạn sẽ xuất hiện tại đây.' : 'Danh sách có thể đã thay đổi. Hãy quay lại trang đầu.'}</p><Link className="friend-request-find" to={page === 0 ? '/friends/search' : '/friends'}>{page === 0 ? 'Tìm bạn →' : 'Về trang đầu →'}</Link></div>}
      {(data.page > 0 || data.hasNext) && <nav className="friend-pagination" aria-label="Phân trang bạn bè"><button className="button button-secondary" type="button" disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>Trang trước</button><span>Trang {data.page + 1}</span><button className="button button-secondary" type="button" disabled={!data.hasNext} onClick={() => onPage(data.page + 1)}>Trang sau</button></nav>}
    </>}
  </section>
}

export function FriendsPage() {
  const { session } = useAuth()
  const [params, setParams] = useSearchParams()
  const rawPage = params.get('page') || '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const [revision, setRevision] = useState(0)
  return <main className="profile-content friends-page">
    <div className="profile-page-heading"><div><span className="eyebrow form-eyebrow">NHỮNG KẾT NỐI CỦA BẠN</span><h1>Bạn bè của tôi</h1><FriendCount key={`${session!.user.id}:${revision}`} userId={session!.user.id} /></div><button className="visitor-refresh" type="button" onClick={() => setRevision(value => value + 1)}>Làm mới</button></div>
    <div className="friend-list-toolbar"><span>Danh sách bạn bè</span><Link to="/friends/search"><Icon name="search" width="15" height="15" />Tìm thêm bạn</Link></div>
    <FriendsList key={`${session!.user.id}:${page}:${revision}`} page={page} onPage={next => setParams(next === 0 ? {} : { page: String(next) })} />
  </main>
}
