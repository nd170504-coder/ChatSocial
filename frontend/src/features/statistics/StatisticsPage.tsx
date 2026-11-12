import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Icon } from '../../components/ui/Icon'
import { statisticsApi, type UserStatistics } from './api'
import './statistics.css'

const metrics = [
  { key: 'postCount', label: 'Bài viết', icon: 'feed', description: 'Tất cả bài viết bạn đã đăng.', tone: 'blue' },
  { key: 'friendCount', label: 'Bạn bè', icon: 'user', description: 'Những người bạn đang kết nối.', tone: 'green' },
  { key: 'receivedLikeCount', label: 'Lượt thích nhận được', icon: 'like', description: 'Lượt thích từ người khác trên bài của bạn.', tone: 'pink' },
  { key: 'receivedCommentCount', label: 'Bình luận nhận được', icon: 'comment', description: 'Gồm bình luận và câu trả lời từ người khác.', tone: 'purple' },
] as const

export function StatisticsPage() {
  const [data, setData] = useState<UserStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => { window.scrollTo(0, 0) }, [])

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true); setError('')
    void statisticsApi.get(controller.signal).then(response => {
      if (active) setData(response.data)
    }).catch(() => {
      if (active) setError('Chưa tải được thống kê. Bạn thử lại nhé.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [attempt])

  return <main className="statistics-page">
    <header className="statistics-heading"><div><span className="eyebrow">GÓC NHÌN CÁ NHÂN</span><h1>Thống kê của tôi</h1><p>Những câu chuyện và kết nối của bạn tại ChatSocial.</p></div>
      <button className="button button-secondary" type="button" disabled={loading} onClick={() => setAttempt(value => value + 1)}>{loading ? 'Đang tải…' : 'Làm mới'}</button>
    </header>
    {error && <p className="statistics-error" role="alert">{error} <button type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button></p>}
    {loading && <p className="statistics-status" role="status">Đang cập nhật số liệu…</p>}
    {!error && <dl className="statistics-grid" aria-busy={loading}>{metrics.map(metric => <div key={metric.key} className={`statistics-card statistics-${metric.tone}`}>
      <dt><span className="statistics-icon"><Icon name={metric.icon} /></span>{metric.label}</dt>
      <dd>{data ? data[metric.key].toLocaleString('vi-VN') : '—'}<p>{metric.description}</p></dd>
    </div>)}</dl>}
    <aside className="statistics-note"><Icon name="lock" /><div><strong>Chỉ bạn xem được thống kê này</strong><p>Tính trên tất cả bài công khai, bạn bè và riêng tư. Lượt thích và bình luận không tính tương tác của chính bạn. Số liệu thay đổi khi bài viết, lượt thích hoặc bình luận bị xóa.</p><Link to="/profile">Xem hồ sơ của tôi →</Link></div></aside>
  </main>
}
