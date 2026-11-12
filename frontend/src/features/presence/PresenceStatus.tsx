import { useEffect, useState } from 'react'
import { usePresence } from './PresenceProvider'
import { formatLastSeen } from './state'

export function PresenceStatus({ userId, enabled }: { userId?: string; enabled: boolean }) {
  const presence = usePresence(userId)
  const lastSeenAt = enabled && presence && !presence.online ? presence.lastSeenAt : null
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (!lastSeenAt) return
    setNow(Date.now())
    // Keep relative text current without requiring new socket messages.
    const timer = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [lastSeenAt])
  if (!enabled || !presence) return null
  if (presence.online) return <small className="chat-person-online" role="status">Đang hoạt động</small>
  const label = formatLastSeen(lastSeenAt, now)
  return label ? <small className="chat-person-last-seen" role="status">{label}</small> : null
}
