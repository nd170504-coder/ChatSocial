import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { friendsApi } from '../api'
import '../friends.css'

export function FriendCount({ userId, linkToList = false }: { userId: string; linkToList?: boolean }) {
  const [count, setCount] = useState<number | null>(null)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void friendsApi.count(userId, controller.signal).then(response => {
      if (active) setCount(response.data.friendCount)
    }).catch(() => { if (active) setError(true) })
    return () => { active = false; controller.abort() }
  }, [userId, attempt])

  if (error) return <span className="friend-count friend-count-error">Chưa tải được số bạn bè. <button type="button" onClick={() => { setError(false); setCount(null); setAttempt(value => value + 1) }}>Thử lại</button></span>
  if (count === null) return <span className="friend-count" role="status">Đang tải số bạn bè…</span>
  const content = <><strong>{count.toLocaleString('vi-VN')}</strong> bạn bè</>
  return linkToList ? <Link className="friend-count friend-count-link" to="/friends">{content}</Link>
    : <span className="friend-count" aria-live="polite">{content}</span>
}
