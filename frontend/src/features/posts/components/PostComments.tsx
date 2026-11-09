import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useOutletContext } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { Avatar } from '../../profile/components/Avatar'
import type { useProfile } from '../../profile/hooks/useProfile'
import { postApi } from '../api'
import type { Post, PostComment } from '../types'
import { postError, postTime } from '../utils'
import { PostBody } from './PostBody'
import { PostLikes, type usePostLikes } from './PostLikes'

export function PostComments({ post, authorName, avatarUrl, likes }: {
  post: Post; authorName: string; avatarUrl?: string | null; likes: ReturnType<typeof usePostLikes>
}) {
  const postId = post.id
  const { session } = useAuth()
  const profileState = useOutletContext<ReturnType<typeof useProfile> | undefined>()
  const panelId = useId()
  const titleId = useId()
  const inputId = useId()
  const modal = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const [count, setCount] = useState<number | null>(null)
  const [countError, setCountError] = useState('')
  const [countAttempt, setCountAttempt] = useState(0)
  const [items, setItems] = useState<PostComment[]>([])
  const [hasNext, setHasNext] = useState(false)
  const [request, setRequest] = useState({ page: 0, attempt: 0 })
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmId, setConfirmId] = useState<string | null>(null)
  const locked = useRef(false)
  const listLocked = useRef(false)
  const mounted = useRef(false)

  useEffect(() => {
    if (!open) return
    const element = modal.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    return () => { element?.close(); document.body.style.overflow = previousOverflow }
  }, [open])

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void postApi.commentCount(postId, controller.signal).then(response => {
      if (active) { setCount(response.data.commentCount); setCountError('') }
    }).catch(cause => { if (active) setCountError(postError(cause)) })
    return () => { active = false; controller.abort() }
  }, [postId, countAttempt])

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    let active = true
    void postApi.comments(postId, request.page, controller.signal).then(response => {
      if (!active) return
      setItems(previous => {
        if (request.page === 0) return response.data.items
        const ids = new Set(previous.map(item => item.id))
        return [...previous, ...response.data.items.filter(item => !ids.has(item.id))]
      })
      setHasNext(response.data.hasNext)
    }).catch(cause => { if (active) setListError(postError(cause)) })
      .finally(() => { if (active) { setLoading(false); listLocked.current = false } })
    return () => { active = false; controller.abort() }
  }, [postId, open, request])

  function load(page: number) {
    listLocked.current = true
    setLoading(true)
    setListError('')
    setRequest(current => ({ page, attempt: current.attempt + 1 }))
  }

  function show() {
    if (locked.current) return
    load(0); setOpen(true)
  }

  function close() {
    if (locked.current) return
    modal.current?.close()
    setOpen(false); setConfirmId(null)
  }

  async function create() {
    const content = draft.trim()
    if (!content || content.length > 2000 || locked.current || listLocked.current) return
    locked.current = true
    setBusy(true); setActionError(''); setNotice('')
    try {
      await postApi.createComment(postId, content)
      if (!mounted.current) return
      setDraft(''); setNotice('Đã gửi bình luận.'); setConfirmId(null)
      load(0)
      setCountAttempt(value => value + 1)
    } catch (cause) {
      if (mounted.current) setActionError(postError(cause))
    } finally {
      locked.current = false
      if (mounted.current) setBusy(false)
    }
  }

  async function remove(commentId: string) {
    if (locked.current || listLocked.current) return
    locked.current = true
    setBusy(true); setActionError(''); setNotice('')
    try {
      const response = await postApi.deleteComment(postId, commentId)
      if (!mounted.current) return
      setItems(previous => previous.filter(item => item.id !== commentId))
      setCount(response.data.commentCount); setCountError('')
      setCountAttempt(value => value + 1)
      setConfirmId(null); setNotice('Đã xóa bình luận.')
      load(0)
    } catch (cause) {
      if (mounted.current) setActionError(postError(cause))
    } finally {
      locked.current = false
      if (mounted.current) setBusy(false)
    }
  }

  const countText = countError ? '—' : count === null ? '…' : count.toLocaleString('vi-VN')
  return <div className="post-comments" aria-label="Bình luận bài viết">
    <button type="button" className="post-comments-toggle" aria-label="Mở bình luận" aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? panelId : undefined} disabled={busy} onClick={show}>
      <Icon name="comment" /><span aria-live="polite">{countText}</span>
    </button>
    {countError && <div className="post-comment-error" role="alert"><span>{countError}</span><button type="button" onClick={() => { setCountError(''); setCount(null); setCountAttempt(value => value + 1) }}>Thử tải lại số bình luận</button></div>}
    {open && createPortal(<dialog ref={modal} id={panelId} className="post-comments-dialog" aria-labelledby={titleId}
      onCancel={event => { if (event.target === event.currentTarget) { event.preventDefault(); close() } }}
      onClick={event => { if (event.target === event.currentTarget) close() }}>
      <header className="post-comments-dialog-heading"><h2 id={titleId}>Bài viết của {authorName}</h2><button type="button" className="icon-button" aria-label="Đóng bình luận" disabled={busy} onClick={close}><Icon name="close" /></button></header>
      <div className="post-comments-scroll">
        <PostBody post={post} authorName={authorName} avatarUrl={avatarUrl} fullContent />
        <div className="post-interactions"><PostLikes state={likes} /><span className="post-comment-tally"><Icon name="comment" />{countText} bình luận</span></div>
        <section className="post-comments-panel" aria-label="Danh sách bình luận">
          <div className="post-comments-heading"><h3>Bình luận</h3><span>Mới nhất trước</span></div>
        {listError && <div className="post-comment-error" role="alert"><span>{listError}</span><button type="button" disabled={busy || loading} onClick={() => load(request.page)}>Thử tải lại bình luận</button></div>}
        <ul className="post-comment-list" aria-busy={loading}>{items.map(comment => {
          const own = comment.userId === session?.user.id
          const profilePath = own ? '/profile' : `/users/${encodeURIComponent(comment.userId)}`
          return <li key={comment.id} className="post-comment-item">
            <Link to={profilePath} aria-label={`Xem hồ sơ của ${comment.fullName}`}><Avatar userId={comment.userId} name={comment.fullName} src={comment.avatarUrl} className="comment-avatar" /></Link>
            <div className="post-comment-body"><div className="post-comment-bubble"><Link to={profilePath} className="post-comment-author">{comment.fullName}</Link><p>{comment.content}</p></div>
              <div className="post-comment-meta"><time dateTime={comment.createdAt}>{postTime(comment.createdAt)}</time>{own && <button type="button" disabled={busy || loading} onClick={() => { setConfirmId(comment.id); setActionError(''); setNotice('') }}>Xóa bình luận</button>}</div>
              {confirmId === comment.id && <div className="post-comment-confirm"><span>Xóa bình luận này?</span><button type="button" disabled={busy || loading} onClick={() => void remove(comment.id)}>{busy ? 'Đang xóa…' : 'Xác nhận xóa'}</button><button type="button" disabled={busy} onClick={() => setConfirmId(null)}>Giữ lại</button></div>}
            </div>
          </li>
        })}</ul>
        {loading && <p className="post-comment-state" role="status">Đang tải bình luận…</p>}
        {!loading && !listError && items.length === 0 && <p className="post-comment-state">Chưa có bình luận. Hãy chia sẻ cảm nghĩ đầu tiên!</p>}
        {hasNext && !listError && <button type="button" className="post-comments-more" disabled={loading || busy} onClick={() => { if (!listLocked.current && !locked.current) load(request.page + 1) }}>Xem thêm bình luận</button>}
        </section>
      </div>
      <footer className="post-comment-composer">
        {notice && <p className="post-comment-notice" role="status">{notice}</p>}
        {actionError && <p className="post-comment-error" role="alert">{actionError}</p>}
        <form className="post-comment-form" onSubmit={event => { event.preventDefault(); void create() }}>
          <Avatar name={profileState?.profile?.fullName || session!.user.fullName} src={profileState?.profile?.avatarUrl} className="comment-composer-avatar" />
          <div className="post-comment-input">
            <label htmlFor={inputId} className="post-comment-label">Viết bình luận</label>
            <textarea id={inputId} value={draft} maxLength={2000} rows={2} disabled={busy} placeholder={`Bình luận dưới tên ${profileState?.profile?.fullName || session!.user.fullName}…`} onChange={event => setDraft(event.target.value)} />
            <div className="post-comment-form-actions"><span>{draft.length.toLocaleString('vi-VN')}/2.000</span><button className="post-comment-send" aria-label="Gửi bình luận" title="Gửi bình luận" type="submit" disabled={busy || loading || !draft.trim()}>{busy && !confirmId ? <span>…</span> : <Icon name="send" />}</button></div>
          </div>
        </form>
      </footer>
    </dialog>, document.body)}
  </div>
}
