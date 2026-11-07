import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../api/client'
import { Icon } from '../../../components/ui/Icon'
import { profileApi } from '../../profile/api'
import type { UserProfile } from '../../profile/types'
import { friendRequestApi } from '../api'
import { friendRequestError, relationshipLabels } from '../utils'
import { UnfriendButton } from './UnfriendButton'
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

  async function mutate(action: 'send' | 'cancel' | 'accept' | 'reject') {
    const expected = action === 'send' ? 'NONE' : action === 'cancel' ? 'OUTGOING_PENDING' : 'INCOMING_PENDING'
    if (locked.current || profile.relationship !== expected) return
    if (action !== 'send' && !profile.friendRequestId) {
      setError('Hãy làm mới hồ sơ để lấy thông tin lời mời.'); return
    }
    locked.current = true; setBusy(true); onBusyChange(true); setError(''); setNotice('')
    try {
      if (action === 'send') {
        const response = await friendRequestApi.send(profile.userId)
        if (active.current) {
          onUpdated({ ...profile, relationship: 'OUTGOING_PENDING', friendRequestId: response.data.id })
          setNotice('Đã gửi lời mời kết bạn.')
        }
      } else if (action === 'cancel') {
        await friendRequestApi.cancel(profile.friendRequestId!)
        if (active.current) {
          onUpdated({ ...profile, relationship: 'NONE', friendRequestId: null })
          setNotice('Đã hủy lời mời kết bạn.')
        }
      } else {
        await friendRequestApi[action](profile.friendRequestId!)
        if (active.current) {
          onUpdated({ ...profile, relationship: action === 'accept' ? 'FRIENDS' : 'NONE', friendRequestId: null })
          setNotice(action === 'accept' ? 'Đã chấp nhận lời mời. Hai bạn đã là bạn bè.' : 'Đã từ chối lời mời kết bạn.')
        }
      }
    } catch (cause) {
      if (!active.current) return
      if (cause instanceof ApiError && (cause.status === 409 || (action !== 'send' && cause.status === 404))) {
        try {
          const response = await profileApi.getUser(profile.userId)
          if (active.current) { onUpdated(response.data); setNotice('Đã cập nhật trạng thái quan hệ.') }
        } catch { if (active.current) setError(friendRequestError(cause, action)) }
      } else setError(friendRequestError(cause, action))
    } finally {
      locked.current = false
      if (active.current) { setBusy(false); onBusyChange(false) }
    }
  }

  if (profile.relationship === 'SELF') return null
  return <div className="visitor-friend-action">
    {profile.relationship === 'NONE'
      ? <button type="button" className="button button-primary visitor-add-friend" disabled={busy} onClick={() => void mutate('send')}><Icon name="user" />{busy ? 'Đang gửi…' : 'Kết bạn'}</button>
      : <span className={`visitor-relationship ${profile.relationship === 'FRIENDS' ? 'is-friend' : ''}`}><Icon name={profile.relationship === 'INCOMING_PENDING' ? 'mail' : 'check'} width="15" height="15" />{relationshipLabels[profile.relationship]}</span>}
    {profile.relationship === 'OUTGOING_PENDING' && <button type="button" className="button button-secondary visitor-cancel-friend" disabled={busy} onClick={() => void mutate('cancel')}><Icon name="close" width="15" height="15" />{busy ? 'Đang hủy…' : 'Hủy lời mời'}</button>}
    {profile.relationship === 'FRIENDS' && <UnfriendButton userId={profile.userId} fullName={profile.fullName}
      onBusyChange={value => { setBusy(value); onBusyChange(value) }}
      onRemoved={updated => { setBusy(false); onBusyChange(false); onUpdated(updated || { ...profile, relationship: 'NONE', friendRequestId: null }); setNotice('Đã cập nhật quan hệ. Bạn chỉ xem được bài công khai của người này.'); setError('') }} />}
    {profile.relationship === 'INCOMING_PENDING' && <div className="friend-response-buttons"><button type="button" className="button button-primary" disabled={busy} onClick={() => void mutate('accept')}>{busy ? 'Đang xử lý…' : 'Chấp nhận'}</button><button type="button" className="button button-secondary" disabled={busy} onClick={() => void mutate('reject')}>Từ chối</button></div>}
    {notice && <span className="friend-action-notice" role="status">{notice}</span>}
    {error && <span className="friend-action-error" role="alert">{error}</span>}
  </div>
}
