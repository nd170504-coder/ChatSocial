import { useEffect, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { postApi } from '../api'
import type { Post, PostPage } from '../types'
import { postError } from '../utils'
import { PostCard } from './PostCard'
import { PostEditor } from './PostEditor'
import '../posts.css'

export function PostSection({ userId, name, avatarUrl, feed = false, readOnly = false }: { userId: string; name: string; avatarUrl?: string | null; feed?: boolean; readOnly?: boolean }) {
  const [data, setData] = useState<PostPage>({ items: [], page: 0, size: 10, hasNext: false })
  const [request, setRequest] = useState({ page: 0, attempt: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const loadingRef = useRef(true)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const pending = feed ? postApi.feed(request.page, controller.signal)
      : postApi.list(userId, request.page, controller.signal)
    void pending.then(response => {
      if (!active) return
      setData(previous => {
        if (request.page === 0) return response.data
        const ids = new Set(previous.items.map(post => post.id))
        return { ...response.data, items: [...previous.items, ...response.data.items.filter(post => !ids.has(post.id))] }
      })
    }).catch(cause => { if (active) setError(postError(cause)) })
      .finally(() => { if (active) { setLoading(false); loadingRef.current = false } })
    return () => { active = false; controller.abort() }
  }, [userId, request, feed])

  function load(page: number) {
    loadingRef.current = true; setLoading(true); setError('')
    setRequest(current => ({ page, attempt: current.attempt + 1 }))
  }

  function saved(post: Post) {
    setData(current => ({ ...current, items: current.items.map(item => item.id === post.id ? post : item) }))
    setNotice('Đã lưu thay đổi của bạn.')
    load(0)
  }

  return <section id="posts" className="posts-section" aria-labelledby="posts-heading">
    <div className="posts-section-heading"><div><span className="eyebrow form-eyebrow">{feed ? 'CÂU CHUYỆN TỪ MỌI NGƯỜI' : readOnly ? 'NHỮNG CÂU CHUYỆN ĐƯỢC CHIA SẺ' : 'NHỮNG ĐIỀU BẠN CHIA SẺ'}</span><h2 id="posts-heading">{feed ? 'Bảng tin' : readOnly ? 'Bài viết' : 'Bài viết của tôi'}</h2></div><span>Mới nhất trước</span></div>
    {!feed && !readOnly && <div className="post-composer"><PostEditor name={name} avatarUrl={avatarUrl} onSave={async (values, files) => {
      await postApi.create(values, files)
      setNotice('Bài viết đã được đăng.'); load(0)
    }} /></div>}
    {notice && <div className="post-notice feedback feedback-success" role="status">{notice}<button type="button" aria-label="Ẩn thông báo" onClick={() => setNotice('')}><Icon name="close" width="15" height="15" /></button></div>}
    {error && <div className="post-list-error feedback feedback-error" role="alert"><span>{error}</span><button type="button" onClick={() => load(request.page)}>Thử lại</button></div>}
    <div className="post-list" aria-busy={loading}>{data.items.map(post => <PostCard key={post.id} post={post} authorName={post.userId === userId ? name : post.authorName || 'Thành viên ChatSocial'} avatarUrl={post.userId === userId ? avatarUrl : post.authorAvatarUrl} onSaved={saved} onDeleted={id => {
      setData(current => ({ ...current, items: current.items.filter(post => post.id !== id) }))
      setNotice('Đã xóa bài viết.'); load(0)
    }} />)}</div>
    {loading && <div className="post-loading" role="status"><span className="post-loading-dot" />Đang tải bài viết…</div>}
    {!loading && !error && data.items.length === 0 && <div className="post-empty"><span className="post-empty-icon"><Icon name="edit" width="28" height="28" /></span><h3>{readOnly ? 'Chưa có bài viết để hiển thị' : feed ? 'Chưa có bài viết trên bảng tin' : 'Câu chuyện đầu tiên bắt đầu từ bạn'}</h3>{!readOnly && <p>{feed ? 'Bài công khai và bài dành cho bạn bè của người khác sẽ xuất hiện tại đây.' : <>Viết vài dòng hoặc chia sẻ một tấm ảnh.<br />Những khoảnh khắc của bạn sẽ xuất hiện ở đây.</>}</p>}</div>}
    {!error && data.hasNext && <button className="button button-secondary post-load-more" type="button" disabled={loading} onClick={() => { if (!loadingRef.current) load(data.page + 1) }}>{loading ? 'Đang tải…' : 'Xem thêm bài viết'}</button>}
    {!loading && !error && data.items.length > 0 && !data.hasNext && <p className="post-list-end">Bạn đã xem hết các bài viết.</p>}
  </section>
}
