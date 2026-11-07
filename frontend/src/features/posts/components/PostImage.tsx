import { useEffect, useState } from 'react'
import { postApi } from '../api'

export function PostImage({ postId, mediaId, index, onOpen, moreCount = 0 }: {
  postId: string; mediaId: string; index: number; onOpen?: () => void; moreCount?: number
}) {
  const [image, setImage] = useState('')
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    let objectUrl: string | undefined
    void postApi.image(postId, mediaId, controller.signal).then(blob => {
      if (!active) return
      objectUrl = URL.createObjectURL(blob)
      setImage(objectUrl)
      setError(false)
    }).catch(() => { if (active) setError(true) })
    return () => {
      active = false
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [postId, mediaId, attempt])

  return <div className="post-image">
    {error ? <div className="post-image-status"><span>Chưa tải được ảnh</span><button type="button" onClick={() => { setError(false); setImage(''); setAttempt(value => value + 1) }}>Thử lại</button></div>
      : image ? <img src={image} alt={`Ảnh ${index + 1} của bài viết`} loading="lazy" onError={() => setError(true)} />
        : <span className="post-image-status" role="status">Đang tải ảnh…</span>}
    {onOpen && <button className="post-image-open" type="button" onClick={onOpen}
      aria-label={`Xem ảnh ${index + 1}${moreCount > 0 ? ` và ${moreCount} ảnh khác` : ''}`}>
      {moreCount > 0 && <span className="post-image-more" aria-hidden="true">+{moreCount}</span>}
    </button>}
  </div>
}
