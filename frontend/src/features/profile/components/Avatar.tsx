import { useState } from 'react'
import { initials } from '../utils'

export function Avatar({ name, src, className = '' }: { name: string; src?: string | null; className?: string }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  return <span className={`profile-avatar ${className}`}>
    {src && failedUrl !== src
      ? <img src={src} alt={`Ảnh đại diện của ${name}`} onError={() => setFailedUrl(src)} />
      : <span aria-label={`Ảnh đại diện mặc định của ${name}`}>{initials(name)}</span>}
  </span>
}
