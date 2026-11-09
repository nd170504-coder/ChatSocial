import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import { Avatar } from '../../profile/components/Avatar'
import { friendSearchApi } from '../api'
import type { UserSearchPage } from '../types'
import { normalizeUserQuery, searchError } from '../utils'
import '../friends.css'

function SearchResults({ query, page, onPage }: { query: string; page: number; onPage: (page: number) => void }) {
  const [data, setData] = useState<UserSearchPage | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void friendSearchApi.search(query, page, controller.signal).then(response => {
      if (active) setData(response.data)
    }).catch(cause => { if (active) setError(searchError(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [query, page, attempt])

  return <section className="friend-search-results" aria-label="Kết quả tìm kiếm" aria-busy={loading}>
    {loading && <p className="friend-search-state" role="status">Đang tìm kiếm người dùng…</p>}
    {error && <div className="feedback feedback-error" role="alert">{error}<button type="button" className="friend-search-retry" onClick={() => { setError(''); setLoading(true); setAttempt(value => value + 1) }}>Thử lại</button></div>}
    {!loading && !error && data && <>
      <div className="friend-results-heading"><h2>Kết quả cho “{query}”</h2><span>Trang {data.page + 1}</span></div>
      {data.items.length > 0 ? <ul className="friend-result-grid">{data.items.map(user => <li key={user.id}><Link className="friend-result-card friend-profile-link" to={`/users/${encodeURIComponent(user.id)}`} aria-label={`Xem hồ sơ của ${user.fullName}`}><Avatar userId={user.id} name={user.fullName} src={user.avatarUrl} className="friend-result-avatar" /><div><h3>{user.fullName}</h3><p>@{user.username}</p></div></Link></li>)}</ul>
        : <div className="friend-search-empty"><Icon name="search" width="30" height="30" /><h3>Chưa tìm thấy người dùng</h3><p>Thử username khác hoặc kiểm tra lại email đầy đủ.</p></div>}
      {(data.page > 0 || data.hasNext) && <nav className="friend-pagination" aria-label="Phân trang kết quả"><button className="button button-secondary" type="button" disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>Trang trước</button><button className="button button-secondary" type="button" disabled={!data.hasNext} onClick={() => onPage(data.page + 1)}>Trang sau</button></nav>}
    </>}
  </section>
}

export function FindFriendsPage() {
  const [params, setParams] = useSearchParams()
  const [validationError, setValidationError] = useState('')
  const rawQuery = params.get('q') || ''
  const rawPage = params.get('page') || '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  let query = ''
  let queryError = ''
  if (rawQuery) {
    try { query = normalizeUserQuery(rawQuery) } catch (cause) { queryError = (cause as Error).message }
  }

  return <main className="profile-content find-friends-page">
    <div className="profile-page-heading"><div><span className="eyebrow form-eyebrow">BẮT ĐẦU MỘT KẾT NỐI</span><h1>Tìm bạn</h1><p>Tìm người bạn biết bằng username hoặc email.</p></div></div>
    <form className="friend-search-form" role="search" key={rawQuery} onSubmit={event => {
      event.preventDefault()
      const value = String(new FormData(event.currentTarget).get('query') || '')
      try { const normalized = normalizeUserQuery(value); setValidationError(''); setParams({ q: normalized }) }
      catch (cause) { setValidationError((cause as Error).message) }
    }}>
      <label htmlFor="friend-search">Username hoặc email</label>
      <div className="friend-search-input"><Icon name="search" /><input id="friend-search" name="query" type="search" defaultValue={rawQuery} maxLength={255} placeholder="Ví dụ: minhanh hoặc ban@example.com" autoComplete="off" aria-describedby="friend-search-hint" /><button className="button button-primary" type="submit">Tìm kiếm</button></div>
      <p id="friend-search-hint">Nhập một phần username (ít nhất 2 ký tự) hoặc email đầy đủ.</p>
    </form>
    {(validationError || queryError) && <div className="feedback feedback-error" role="alert">{validationError || queryError}</div>}
    {query ? <SearchResults key={`${query}:${page}`} query={query} page={page} onPage={next => setParams({ q: query, page: String(next) })} />
      : !queryError && <div className="friend-search-empty friend-search-welcome"><span className="friend-search-illustration"><Icon name="user" width="38" height="38" /><Icon name="search" width="22" height="22" /></span><h2>Tìm thấy nhau, bắt đầu câu chuyện</h2><p>Nhập username hoặc email của người bạn muốn tìm.<br />Kết quả sẽ xuất hiện tại đây.</p></div>}
  </main>
}
