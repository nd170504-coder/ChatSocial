import { useEffect, useState } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router'
import { useAuth } from '../../auth/hooks/useAuth'
import type { useProfile } from '../../profile/hooks/useProfile'
import { postApi } from '../api'
import type { Post } from '../types'
import { postError } from '../utils'
import { PostCard } from '../components/PostCard'
import '../posts.css'

export function PostPage() {
  const { postId = '' } = useParams()
  return <PostDetail key={postId} postId={postId} />
}

function PostDetail({ postId }: { postId: string }) {
  const { session } = useAuth()
  const { profile } = useOutletContext<ReturnType<typeof useProfile>>()
  const navigate = useNavigate()
  const [post, setPost] = useState<Post | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void postApi.get(postId, controller.signal).then(response => {
      if (active) setPost(response.data)
    }).catch(cause => { if (active) setError(postError(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [postId, attempt])
  const own = post?.userId === session?.user.id
  return <main className="profile-content post-detail-page"><Link className="post-back-link" to="/feed">← Về bài viết công khai</Link><div className="profile-page-heading"><h1>Chi tiết bài viết</h1></div>
    {loading && <p role="status" className="post-loading">Đang tải bài viết…</p>}
    {error && <div className="feedback feedback-error" role="alert">{error}<button className="post-retry" type="button" onClick={() => { setError(''); setLoading(true); setAttempt(value => value + 1) }}>Thử lại</button></div>}
    {!loading && !error && post && <PostCard post={post} authorName={own ? profile?.fullName || session!.user.fullName : post.authorName || 'Thành viên ChatSocial'} avatarUrl={own ? profile?.avatarUrl : post.authorAvatarUrl} onSaved={setPost} onDeleted={() => { void navigate('/feed', { replace: true }) }} />}
  </main>
}
