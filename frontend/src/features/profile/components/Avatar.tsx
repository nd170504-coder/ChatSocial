import { useState } from 'react'
import { initials } from '../utils'
import { useOnline } from '../../presence/PresenceProvider'

export function Avatar({ name, src, className = '', userId }: { name: string; src?: string | null; className?: string; userId?: string }) {
  const online = useOnline(userId)
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  return <span className={`profile-avatar ${className}`}>
    {src && failedUrl !== src
      ? <img src={src} alt={`Ảnh đại diện của ${name}`} onError={() => setFailedUrl(src)} />
      : <span aria-label={`Ảnh đại diện mặc định của ${name}`}>{initials(name)}</span>}
    {online && <span className="presence-dot" role="img" aria-label={`${name} đang hoạt động`} title="Đang hoạt động" />}
  </span>
}
