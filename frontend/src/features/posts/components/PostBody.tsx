import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { Avatar } from '../../profile/components/Avatar'
import type { Post } from '../types'
import { postTime, privacyLabels } from '../utils'
import { PostGallery } from './PostGallery'

export function PostBody({ post, authorName, avatarUrl, tools, fullContent = false }: {
  post: Post; authorName: string; avatarUrl?: string | null; tools?: ReactNode; fullContent?: boolean
}) {
  const { session } = useAuth()
  const profilePath = post.userId === session?.user.id ? '/profile' : `/users/${encodeURIComponent(post.userId)}`
  const [expanded, setExpanded] = useState(false)
  const long = (post.content?.length || 0) > 600
  return <>
    <header className="post-card-header"><Link className="post-author-avatar" to={profilePath} aria-label={`Xem hồ sơ của ${authorName}`}><Avatar name={authorName} src={avatarUrl} className="post-avatar" /></Link><div className="post-card-author"><strong><Link className="post-author-name" to={profilePath}>{authorName}</Link></strong><div><Link to={`/posts/${post.id}`} aria-label="Xem chi tiết bài viết"><time dateTime={post.createdAt}>{postTime(post.createdAt)}</time></Link><span aria-hidden="true">·</span><span title={privacyLabels[post.privacy]}><Icon name={post.privacy === 'PRIVATE' ? 'lock' : 'shield'} width="12" height="12" />{privacyLabels[post.privacy]}</span></div></div>{tools}</header>
    {post.content && <div className="post-card-content"><p>{long && !expanded && !fullContent ? `${post.content.slice(0, 600)}…` : post.content}</p>{long && !fullContent && <button type="button" className="post-text-toggle" onClick={() => setExpanded(value => !value)}>{expanded ? 'Thu gọn' : 'Xem thêm'}</button>}</div>}
    <PostGallery postId={post.id} media={post.media} />
    {post.updatedAt !== post.createdAt && <p className="post-edited">Đã chỉnh sửa</p>}
  </>
}
