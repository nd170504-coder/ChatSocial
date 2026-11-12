import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../../../api/client'
import { profileApi } from '../../profile/api'
import type { UserProfile } from '../../profile/types'
import { friendsApi } from '../api'

export function UnfriendButton({ userId, fullName, onRemoved, onBusyChange, disabled = false, leadingIcon }: {
  userId: string
  fullName: string
  onRemoved: (profile?: UserProfile) => void
  onBusyChange?: (busy: boolean) => void
  disabled?: boolean
  leadingIcon?: ReactNode
}) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const locked = useRef(false)
  const active = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])

  async function remove() {
    if (locked.current || disabled) return
    locked.current = true
    setBusy(true); setError(''); onBusyChange?.(true)
    try {
      await friendsApi.unfriend(userId)
      if (active.current) onRemoved()
    } catch (cause) {
      if (!active.current) return
      if (cause instanceof ApiError && cause.status === 404) {
        try {
          const response = await profileApi.getUser(userId)
          if (!active.current) return
          if (response.data.relationship !== 'FRIENDS') { onRemoved(response.data); return }
        } catch { /* Keep the action retryable if the current relationship cannot be verified. */ }
      }
      if (active.current) setError(cause instanceof ApiError && cause.status === 401
        ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : 'Chưa thể hủy kết bạn. Vui lòng thử lại hoặc làm mới để kiểm tra quan hệ.')
    } finally {
      locked.current = false
      if (active.current) { setBusy(false); onBusyChange?.(false) }
    }
  }

  return <div className="unfriend-action">
    {!confirming ? <button type="button" className="unfriend-trigger" disabled={disabled} onClick={() => setConfirming(true)}>{leadingIcon}Hủy kết bạn</button>
      : <div className="unfriend-confirm"><p>Hủy kết bạn với <strong>{fullName}</strong>?</p><span>Hai bạn sẽ chỉ xem được bài công khai của nhau.</span><div><button type="button" disabled={busy || disabled} className="unfriend-remove" onClick={() => void remove()}>{busy ? 'Đang hủy…' : 'Xác nhận hủy kết bạn'}</button><button type="button" disabled={busy || disabled} onClick={() => { setConfirming(false); setError('') }}>Giữ bạn bè</button></div></div>}
    {error && <p className="unfriend-error" role="alert">{error}</p>}
  </div>
}
