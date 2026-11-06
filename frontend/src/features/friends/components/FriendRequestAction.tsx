import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../api/client'
import { Icon } from '../../../components/ui/Icon'
import { profileApi } from '../../profile/api'
import type { UserProfile } from '../../profile/types'
import { friendRequestApi } from '../api'
import { friendRequestError, relationshipLabels } from '../utils'
import '../friends.css'

export function FriendRequestAction({ profile, onUpdated, onBusyChange }: {
  profile: UserProfile
  onUpdated: (profile: UserProfile) => void
  onBusyChange: (busy: boolean) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const locked = useRef(false)
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])

  async function send() {
    if (locked.current || profile.relationship !== 'NONE') return
    locked.current = true; setBusy(true); onBusyChange(true); setError(''); setNotice('')
    try {
      await friendRequestApi.send(profile.userId)
      if (active.current) {
        onUpdated({ ...profile, relationship: 'OUTGOING_PENDING' })
        setNotice('Đã gửi lời mời kết bạn.')
      }
    } catch (cause) {
      if (!active.current) return
      if (cause instanceof ApiError && cause.status === 409) {
        try {
          const response = await profileApi.getUser(profile.userId)
          if (active.current) { onUpdated(response.data); setNotice('Đã cập nhật trạng thái quan hệ.') }
        } catch { if (active.current) setError(friendRequestError(cause)) }
      } else setError(friendRequestError(cause))
    } finally {
      locked.current = false
      if (active.current) { setBusy(false); onBusyChange(false) }
    }
  }

  if (profile.relationship === 'SELF') return null
  return <div className="visitor-friend-action">
    {profile.relationship === 'NONE'
      ? <button type="button" className="button button-primary visitor-add-friend" disabled={busy} onClick={() => void send()}><Icon name="user" />{busy ? 'Đang gửi…' : 'Kết bạn'}</button>
      : <span className={`visitor-relationship ${profile.relationship === 'FRIENDS' ? 'is-friend' : ''}`}><Icon name={profile.relationship === 'INCOMING_PENDING' ? 'mail' : 'check'} width="15" height="15" />{relationshipLabels[profile.relationship]}</span>}
    {notice && <span className="friend-action-notice" role="status">{notice}</span>}
    {error && <span className="friend-action-error" role="alert">{error}</span>}
  </div>
}
