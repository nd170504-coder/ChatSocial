import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { Icon } from '../../components/ui/Icon'
import { adminApi, type AdminOverview } from './api'

const statusStyles = {
  ACTIVE: { label: 'Đang hoạt động', color: '#36a68c' },
  PENDING_VERIFICATION: { label: 'Chờ xác minh', color: '#e4ad4d' },
  SUSPENDED: { label: 'Đã khóa', color: '#d76b82' },
  DISABLED: { label: 'Đã vô hiệu hóa', color: '#9c8cce' },
  DELETED: { label: 'Đã xóa', color: '#a5b1c4' },
}
const number = (value: number) => value.toLocaleString('vi-VN')
const shortDate = (date: string) => `${date.slice(8, 10)}/${date.slice(5, 7)}`

export function AdminOverviewPage() {
  const [days, setDays] = useState(7)
  const [attempt, setAttempt] = useState(0)
  const [data, setData] = useState<AdminOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true); setError('')
    void adminApi.overview(days, controller.signal).then(response => { if (active) setData(response.data) })
      .catch(error => { if (active) setError(error instanceof ApiError && error.status === 403
        ? 'Bạn không còn quyền quản trị. Vui lòng đăng nhập lại.' : 'Chưa tải được tổng quan. Vui lòng thử lại.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [days, attempt])

  return <main className="admin-page admin-overview">
    <header className="admin-heading"><div><span className="eyebrow">QUẢN TRỊ CHATSOCIAL</span><h1>Tổng quan</h1><p>Theo dõi người dùng và tình hình tài khoản trong hệ thống.</p></div>
      <div className="admin-overview-toolbar"><label>Khoảng thời gian<select value={days} onChange={event => setDays(Number(event.target.value))}><option value={7}>7 ngày gần nhất</option><option value={30}>30 ngày gần nhất</option></select></label>
        <button className="button button-secondary" type="button" disabled={loading} onClick={() => setAttempt(value => value + 1)}>Làm mới</button></div>
    </header>
    {loading && <p className="admin-state" role="status">Đang cập nhật tổng quan…</p>}
    {error && <p className="admin-error" role="alert">{error} <button type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button></p>}
    {!loading && !error && data && <>
      <dl className="admin-metrics">
        {[
          { label: 'Tổng người dùng', value: data.totalUsers, icon: 'user', tone: 'blue', note: 'Tất cả trạng thái tài khoản' },
          { label: 'Tài khoản mới hôm nay', value: data.newUsersToday, icon: 'statistics', tone: 'purple', note: 'Từ 00:00 theo giờ Việt Nam' },
          { label: 'Đang hoạt động', value: data.statuses.find(item => item.status === 'ACTIVE')?.count || 0, icon: 'user', tone: 'green', note: 'Tài khoản có quyền truy cập' },
          { label: 'Tài khoản đã khóa', value: data.statuses.find(item => item.status === 'SUSPENDED')?.count || 0, icon: 'lock', tone: 'pink', note: 'Đang bị tạm khóa bởi admin' },
        ].map(metric => <div className={`admin-metric admin-metric-${metric.tone}`} key={metric.label}><dt><span><Icon name={metric.icon as 'user' | 'statistics' | 'lock'} /></span>{metric.label}</dt><dd>{number(metric.value)}</dd><p>{metric.note}</p></div>)}
      </dl>
      <div className="admin-charts">
        <section className="admin-chart-card admin-registration-card" aria-labelledby="registration-heading"><div className="admin-chart-heading"><div><h2 id="registration-heading">Tài khoản mới</h2><p>{days} ngày gần nhất · Giờ Việt Nam (UTC+7)</p></div><span className="admin-chart-total">+{number(data.newUsersInPeriod)}<small>tài khoản</small></span></div>
          <RegistrationsChart items={data.registrations} />
          {data.newUsersInPeriod === 0 && <p className="admin-chart-empty">Chưa có tài khoản mới trong khoảng thời gian này.</p>}
          <details className="admin-chart-data"><summary>Xem số liệu theo ngày</summary><div className="admin-daily-values">{data.registrations.map(item => <div key={item.date}><span>{shortDate(item.date)}</span><strong>{number(item.count)}</strong></div>)}</div></details>
        </section>
        <section className="admin-chart-card" aria-labelledby="status-heading"><div className="admin-chart-heading"><div><h2 id="status-heading">Trạng thái tài khoản</h2><p>Phân bố hiện tại trên toàn hệ thống</p></div></div><StatusChart data={data} /></section>
      </div>
      <div className="admin-overview-note"><span>Tổng số liệu bao gồm tài khoản admin và tài khoản đã xóa mềm.</span><Link to="/admin/users">Quản trị người dùng →</Link></div>
    </>}
  </main>
}

function RegistrationsChart({ items }: { items: AdminOverview['registrations'] }) {
  const titleId = useId()
  const step = Math.max(1, Math.ceil(Math.max(...items.map(item => item.count), 1) / 4))
  const max = step * 4
  const plot = { left: 48, top: 24, width: 600, height: 200 }
  const slot = plot.width / items.length
  return <svg className="admin-registration-chart" viewBox="0 0 680 270" role="img" aria-labelledby={titleId}>
    <title id={titleId}>Biểu đồ số tài khoản mới theo ngày. Số liệu chi tiết ở mục Xem số liệu theo ngày.</title>
    {[0, 1, 2, 3, 4].map(tick => {
      const y = plot.top + plot.height - tick * plot.height / 4
      return <g key={tick}><line x1={plot.left} x2={plot.left + plot.width} y1={y} y2={y} stroke="#e9eef6" strokeDasharray="4 5" /><text x={plot.left - 10} y={y + 4} textAnchor="end" className="admin-axis-label">{number(tick * step)}</text></g>
    })}
    {items.map((item, index) => {
      const height = item.count / max * plot.height
      const x = plot.left + index * slot + slot / 2
      const showLabel = items.length <= 7 || index % 5 === 0 || index === items.length - 1
      return <g key={item.date}><rect className="admin-chart-bar" x={x - slot * .28} y={plot.top + plot.height - height} width={slot * .56} height={Math.max(height, 1)} rx={Math.min(slot * .15, 5)} fill={item.count ? '#527bea' : '#dce5f6'}><title>{shortDate(item.date)}: {number(item.count)} tài khoản mới</title></rect>
        {showLabel && <text x={x} y={plot.top + plot.height + 26} textAnchor="middle" className="admin-axis-label">{shortDate(item.date)}</text>}</g>
    })}
  </svg>
}

function StatusChart({ data }: { data: AdminOverview }) {
  const titleId = useId()
  const radius = 70
  const circumference = 2 * Math.PI * radius
  const segments = data.statuses.map((item, index) => ({ ...item,
    offset: data.statuses.slice(0, index).reduce((sum, previous) => sum + previous.count, 0),
  }))
  return <><svg className="admin-status-chart" viewBox="0 0 220 200" role="img" aria-labelledby={titleId}>
    <title id={titleId}>Phân bố trạng thái của {number(data.totalUsers)} tài khoản. Số liệu chi tiết bên dưới.</title>
    <circle cx="110" cy="100" r={radius} fill="none" stroke="#edf1f7" strokeWidth="22" />
    {segments.filter(item => item.count > 0).map(item => <circle key={item.status} cx="110" cy="100" r={radius} fill="none" stroke={statusStyles[item.status].color} strokeWidth="22"
      strokeDasharray={`${item.count / data.totalUsers * circumference} ${circumference}`} strokeDashoffset={-item.offset / data.totalUsers * circumference} transform="rotate(-90 110 100)"><title>{statusStyles[item.status].label}: {number(item.count)}</title></circle>)}
    <text x="110" y="100" textAnchor="middle" className="admin-donut-number">{number(data.totalUsers)}</text><text x="110" y="122" textAnchor="middle" className="admin-axis-label">tài khoản</text>
  </svg><ul className="admin-status-legend">{data.statuses.map(item => <li key={item.status}><span className="admin-legend-dot" style={{ background: statusStyles[item.status].color }} /><span>{statusStyles[item.status].label}</span><strong>{number(item.count)}</strong><small>{data.totalUsers ? (item.count / data.totalUsers * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) : '0'}%</small></li>)}</ul></>
}
