import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { blocksApi, type BlockPage } from './api'
import { BlockButton } from './BlockButton'
import { Avatar } from '../profile/components/Avatar'

export function BlockedUsersPage() {
  const [data, setData] = useState<BlockPage | null>(null), [page, setPage] = useState(0), [attempt, setAttempt] = useState(0)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true); setError('')
    void blocksApi.list(page, controller.signal).then(response => {
      if (active) setData(previous => page === 0 || !previous ? response.data : { ...response.data, items: [...previous.items, ...response.data.items.filter(item => !previous.items.some(old => old.userId === item.userId))] })
    }).catch(() => { if (active) setError('Chưa tải được danh sách đã chặn.') }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [page, attempt])
  return <main className="blocked-page"><header><div><span className="eyebrow">QUẢN LÝ KẾT NỐI</span><h1>Tài khoản đã chặn</h1><p>Bỏ chặn không tự khôi phục kết bạn hay lời mời cũ.</p></div><Link to="/friends">Về bạn bè</Link></header>
    {error && <p role="alert">{error} <button type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button></p>}
    <ul className="blocked-list">{data?.items.map(user => <li key={user.userId}><Avatar name={user.fullName} src={user.avatarUrl} className="blocked-avatar" /><div><strong>{user.fullName}</strong><small>@{user.username}</small></div><BlockButton userId={user.userId} fullName={user.fullName} initiallyBlocked /></li>)}</ul>
    {loading && <p role="status">Đang tải danh sách…</p>}
    {!loading && !error && data?.items.length === 0 && <p>Bạn chưa chặn tài khoản nào.</p>}
    {data?.hasNext && <button className="button button-secondary" type="button" disabled={loading} onClick={() => setPage(data.page + 1)}>Xem thêm</button>}
  </main>
}
