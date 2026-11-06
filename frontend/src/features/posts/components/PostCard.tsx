import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { Avatar } from '../../profile/components/Avatar'
import { postApi } from '../api'
import type { Post } from '../types'
import { postError, postTime, privacyLabels } from '../utils'
import { PostEditor } from './PostEditor'
import { PostImage } from './PostImage'

function PostDialog({ post, mode, name, avatarUrl, onClose, onSaved, onDeleted }: {
  post: Post; mode: 'edit' | 'delete'; name: string; avatarUrl?: string | null
  onClose: () => void; onSaved: (post: Post) => void; onDeleted: (id: string) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const locked = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const titleId = useId()
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  return <dialog ref={dialog} className="image-dialog post-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!locked.current) onClose() }}>
    <div className="dialog-heading"><h2 id={titleId}>{mode === 'edit' ? 'Chỉnh sửa bài viết' : 'Xóa bài viết?'}</h2><button type="button" className="icon-button" disabled={busy} aria-label="Đóng hộp thoại" onClick={onClose}><Icon name="close" /></button></div>
    {mode === 'edit' ? <PostEditor name={name} avatarUrl={avatarUrl} initial={post} onCancel={onClose}
      onBusyChange={value => { locked.current = value; setBusy(value) }}
      onSave={async values => { const response = await postApi.update(post.id, values); onSaved(response.data); onClose() }} />
      : <><p className="dialog-description">Bài viết và các ảnh đính kèm sẽ bị xóa vĩnh viễn. Bạn không thể khôi phục sau khi xóa.</p>
        {error && <div className="feedback feedback-error" role="alert">{error}</div>}
        <div className="post-dialog-actions"><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>Giữ bài viết</button><button className="button post-delete-button" type="button" disabled={busy} onClick={async () => {
          if (locked.current) return
          locked.current = true; setBusy(true); setError('')
          try { await postApi.remove(post.id); onDeleted(post.id); onClose() }
          catch (cause) { setError(postError(cause)) }
          finally { locked.current = false; setBusy(false) }
        }}>{busy ? 'Đang xóa…' : 'Xóa bài viết'}</button></div></>}
  </dialog>
}

export function PostCard({ post, authorName, avatarUrl, onSaved, onDeleted }: {
  post: Post; authorName: string; avatarUrl?: string | null
  onSaved: (post: Post) => void; onDeleted: (id: string) => void
}) {
  const { session } = useAuth()
  const [dialog, setDialog] = useState<'edit' | 'delete' | null>(null)
  const own = post.userId === session?.user.id
  const profilePath = own ? '/profile' : `/users/${encodeURIComponent(post.userId)}`
  const [expanded, setExpanded] = useState(false)
  const long = (post.content?.length || 0) > 600
  const images = [...post.media].filter(media => media.mediaType === 'IMAGE').sort((a, b) => a.displayOrder - b.displayOrder)

  return <article className="post-card">
    <header className="post-card-header"><Link className="post-author-avatar" to={profilePath} aria-label={`Xem hồ sơ của ${authorName}`}><Avatar name={authorName} src={avatarUrl} className="post-avatar" /></Link><div className="post-card-author"><strong><Link className="post-author-name" to={profilePath}>{authorName}</Link></strong><div><Link to={`/posts/${post.id}`} aria-label="Xem chi tiết bài viết"><time dateTime={post.createdAt}>{postTime(post.createdAt)}</time></Link><span aria-hidden="true">·</span><span title={privacyLabels[post.privacy]}><Icon name={post.privacy === 'PRIVATE' ? 'lock' : 'shield'} width="12" height="12" />{privacyLabels[post.privacy]}</span></div></div>
      {own && <div className="post-card-tools"><button type="button" className="icon-button" aria-label="Chỉnh sửa bài viết" onClick={() => setDialog('edit')}><Icon name="edit" width="16" height="16" /></button><button type="button" className="icon-button" aria-label="Xóa bài viết" onClick={() => setDialog('delete')}><Icon name="trash" width="16" height="16" /></button></div>}
    </header>
    {post.content && <div className="post-card-content"><p>{long && !expanded ? `${post.content.slice(0, 600)}…` : post.content}</p>{long && <button type="button" className="post-text-toggle" onClick={() => setExpanded(value => !value)}>{expanded ? 'Thu gọn' : 'Xem thêm'}</button>}</div>}
    {images.length > 0 && <div className={`post-media-grid post-media-${images.length}`}>{images.map((media, index) => <PostImage key={media.id} postId={post.id} mediaId={media.id} index={index} />)}</div>}
    {post.updatedAt !== post.createdAt && <p className="post-edited">Đã chỉnh sửa</p>}
    {dialog && <PostDialog post={post} mode={dialog} name={authorName} avatarUrl={avatarUrl} onClose={() => setDialog(null)} onSaved={onSaved} onDeleted={onDeleted} />}
  </article>
}
