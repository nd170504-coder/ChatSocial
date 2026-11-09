import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Icon } from '../../components/ui/Icon'
import { chatApi } from './api'
import { chatError } from './utils'

export function StartChatButton({ userId }: { userId: string }) {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const locked = useRef(false), active = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  return <div className="start-chat-action"><button type="button" className="button button-secondary start-chat-button" disabled={busy} onClick={async () => {
    if (locked.current) return
    locked.current = true; setBusy(true); setError('')
    try {
      const response = await chatApi.open(userId)
      if (active.current) navigate(`/messages/${encodeURIComponent(response.data.id)}`)
    } catch (cause) { if (active.current) setError(chatError(cause)) }
    finally { locked.current = false; if (active.current) setBusy(false) }
  }}><Icon name="comment" />{busy ? 'Đang mở…' : 'Nhắn tin'}</button>{error && <p className="friend-action-error" role="alert">{error}</p>}</div>
}
