import { useEffect, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { postApi } from '../api'
import type { PostLikeStatus } from '../types'
import { postError } from '../utils'

export function PostLikes({ postId }: { postId: string }) {
  const [data, setData] = useState<PostLikeStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const locked = useRef(false)
  const mounted = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void postApi.likes(postId, controller.signal).then(response => {
      if (active) setData(response.data)
    }).catch(cause => { if (active) setError(postError(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [postId, attempt])

  async function toggle() {
    if (!data || loading || locked.current) return
    locked.current = true
    setBusy(true)
    setError('')
    try {
      const response = await (data.likedByMe ? postApi.unlike(postId) : postApi.like(postId))
      if (mounted.current) setData(response.data)
    } catch (cause) {
      if (mounted.current) {
        // Reload the actual status before another action after an uncertain response.
        setData(null)
        setError(postError(cause))
      }
    } finally {
      locked.current = false
      if (mounted.current) setBusy(false)
    }
  }

  return <footer className="post-likes" aria-label="Lượt thích bài viết">
    <div className="post-likes-row" aria-busy={loading || busy}>
      <button className={`post-like-button${data?.likedByMe ? ' is-liked' : ''}`} type="button"
        aria-label={data?.likedByMe ? 'Bỏ thích bài viết' : 'Thích bài viết'} aria-pressed={data?.likedByMe ?? false}
        disabled={!data || loading || busy} onClick={() => void toggle()}>
        <Icon name="like" /><span>{busy ? 'Đang xử lý…' : data?.likedByMe ? 'Đã thích' : 'Thích'}</span>
      </button>
      <span className="post-like-count" aria-live="polite">
        {loading ? 'Đang tải lượt thích…' : data ? `${data.likeCount.toLocaleString('vi-VN')} lượt thích` : 'Chưa tải được lượt thích'}
      </span>
    </div>
    {error && <div className="post-like-error" role="alert"><span>{error}</span>
      <button type="button" disabled={loading || busy} onClick={() => { setError(''); setLoading(true); setAttempt(value => value + 1) }}>Thử lại</button>
    </div>}
  </footer>
}
