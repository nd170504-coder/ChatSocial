import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Avatar } from '../../profile/components/Avatar'
import { useAuth } from '../../auth/hooks/useAuth'
import { postApi } from '../api'
import type { PostComment } from '../types'
import { postError, postTime } from '../utils'

export function CommentReplies({ postId, parent, autoOpen, onDeleted }: {
  postId: string; parent: PostComment; autoOpen: boolean; onDeleted: (totalCount: number) => void
}) {
  const { session } = useAuth()
  const [open, setOpen] = useState(autoOpen), [items, setItems] = useState<PostComment[]>([])
  const [request, setRequest] = useState({ page: 0, attempt: 0 })
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [hasNext, setHasNext] = useState(false)
  const [deleting, setDeleting] = useState(false), [confirmId, setConfirmId] = useState<string | null>(null)
  const active = useRef(false), locked = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  useEffect(() => {
    if (!open) return
    let current = true
    const controller = new AbortController()
    setLoading(true); setError('')
    void postApi.replies(postId, parent.id, request.page, controller.signal).then(response => {
      if (!current) return
      setItems(previous => request.page === 0 ? response.data.items : [...previous, ...response.data.items.filter(item => !previous.some(old => old.id === item.id))])
      setHasNext(response.data.hasNext)
    }).catch(cause => { if (current) setError(postError(cause)) }).finally(() => { if (current) setLoading(false) })
    return () => { current = false; controller.abort() }
  }, [open, postId, parent.id, request])

  async function remove(id: string) {
    if (locked.current || loading) return
    locked.current = true; setDeleting(true); setError('')
    try {
      const response = await postApi.deleteComment(postId, id)
      if (!active.current) return
      setConfirmId(null); setItems(previous => previous.filter(item => item.id !== id))
      onDeleted(response.data.commentCount)
      setRequest(value => ({ page: 0, attempt: value.attempt + 1 }))
    } catch (cause) { if (active.current) setError(postError(cause)) }
    finally { locked.current = false; if (active.current) setDeleting(false) }
  }

  if (!parent.replyCount && !open) return null
  return <div className="comment-replies">
    <button type="button" className="comment-replies-toggle" aria-expanded={open} disabled={deleting} onClick={() => {
      if (!open) setRequest(value => ({ page: 0, attempt: value.attempt + 1 }))
      setOpen(value => !value)
    }}>{open ? 'Ẩn câu trả lời' : `Xem ${parent.replyCount} câu trả lời`}</button>
    {open && <section aria-label={`Câu trả lời cho ${parent.fullName}`}>
      <span className="comment-replies-order">Câu trả lời · Mới nhất trước</span>
      {error && <div className="post-comment-error" role="alert"><span>{error}</span><button type="button" disabled={loading || deleting} onClick={() => setRequest(value => ({ ...value, attempt: value.attempt + 1 }))}>Thử lại câu trả lời</button></div>}
      <ul className="post-comment-list" aria-busy={loading}>{items.map(reply => {
        const own = reply.userId === session?.user.id
        const profilePath = own ? '/profile' : `/users/${encodeURIComponent(reply.userId)}`
        return <li key={reply.id} className="post-comment-item"><Link to={profilePath} aria-label={`Xem hồ sơ của ${reply.fullName}`}><Avatar userId={reply.userId} name={reply.fullName} src={reply.avatarUrl} className="comment-avatar" /></Link>
          <div className="post-comment-body"><div className="post-comment-bubble"><Link to={profilePath} className="post-comment-author">{reply.fullName}</Link><p>{reply.content}</p></div>
            <div className="post-comment-meta"><time dateTime={reply.createdAt}>{postTime(reply.createdAt)}</time>{own && <button type="button" disabled={deleting || loading} onClick={() => { setConfirmId(reply.id); setError('') }}>Xóa câu trả lời</button>}</div>
            {confirmId === reply.id && <div className="post-comment-confirm"><span>Xóa câu trả lời này?</span><button type="button" disabled={deleting || loading} onClick={() => void remove(reply.id)}>Xác nhận xóa câu trả lời</button><button type="button" disabled={deleting} onClick={() => setConfirmId(null)}>Giữ lại</button></div>}
          </div></li>
      })}</ul>
      {loading && <p className="post-comment-state" role="status">Đang tải câu trả lời…</p>}
      {!loading && !error && !items.length && <p className="post-comment-state">Chưa có câu trả lời.</p>}
      {hasNext && !error && <button type="button" className="post-comments-more" disabled={loading || deleting} onClick={() => setRequest(value => ({ page: value.page + 1, attempt: value.attempt + 1 }))}>Xem thêm câu trả lời</button>}
    </section>}
  </div>
}
