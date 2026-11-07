import { useEffect, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { postApi } from '../api'
import type { PostLikeStatus } from '../types'
import { postError } from '../utils'

export function usePostLikes(postId: string) {
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

  function retry() { setError(''); setLoading(true); setAttempt(value => value + 1) }
  return { data, loading, busy, error, toggle, retry }
}

export function PostLikes({ state }: { state: ReturnType<typeof usePostLikes> }) {
  const { data, loading, busy, error, toggle, retry } = state
  return <div className="post-likes" aria-label="Lượt thích bài viết">
    <div aria-busy={loading || busy}>
      <button className={`post-like-button${data?.likedByMe ? ' is-liked' : ''}`} type="button"
        aria-label={data?.likedByMe ? 'Bỏ thích bài viết' : 'Thích bài viết'} aria-pressed={data?.likedByMe ?? false}
        disabled={!data || loading || busy} onClick={() => void toggle()}>
        <Icon name="like" /><span aria-live="polite">{loading || busy ? '…' : data ? data.likeCount.toLocaleString('vi-VN') : '—'}</span>
      </button>
    </div>
    {error && <div className="post-like-error" role="alert"><span>{error}</span>
      <button type="button" disabled={loading || busy} onClick={retry}>Thử lại</button>
    </div>}
  </div>
}
