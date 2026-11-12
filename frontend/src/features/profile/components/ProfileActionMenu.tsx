import { useEffect, useId, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { BlockButton } from '../../blocks/BlockButton'
import { UnfriendButton } from '../../friends/components/UnfriendButton'
import type { UserProfile } from '../types'

export function ProfileActionMenu({ profile, onRemoved, onBusyChange, disabled = false }: {
  profile: UserProfile; onRemoved: (updated?: UserProfile) => void; onBusyChange: (busy: boolean) => void; disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const wrapper = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const id = useId()

  useEffect(() => {
    if (!open) return
    panel.current?.querySelector('button')?.focus({ preventScroll: true })
  }, [open])

  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent) => {
      const target = event.target
      // The existing block confirmation is rendered in a portal outside this menu.
      if (!busy && target instanceof Element && !wrapper.current?.contains(target)
        && !target.closest('.chat-recall-popover')) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) { setOpen(false); trigger.current?.focus({ preventScroll: true }) }
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) }
  }, [open, busy])

  function updateBusy(value: boolean) { setBusy(value); onBusyChange(value) }

  return <div className="profile-actions-menu" ref={wrapper}>
    <button ref={trigger} type="button" className="profile-more-button" aria-label="Tùy chọn hồ sơ" title="Tùy chọn hồ sơ" aria-expanded={open} aria-controls={open ? id : undefined} disabled={busy || disabled} onClick={() => setOpen(value => !value)}><Icon name="more" /></button>
    {open && <div ref={panel} id={id} className="profile-actions-panel" role="group" aria-label="Thao tác với người dùng">
      {profile.relationship === 'FRIENDS' && <UnfriendButton leadingIcon={<Icon name="unfriend" />} userId={profile.userId} fullName={profile.fullName} disabled={busy || disabled} onBusyChange={updateBusy} onRemoved={updated => {
        setOpen(false); updateBusy(false); onRemoved(updated); trigger.current?.focus({ preventScroll: true })
      }} />}
      <BlockButton leadingIcon={<Icon name="ban" />} userId={profile.userId} fullName={profile.fullName} label="Chặn người dùng" disabled={busy || disabled} onBusyChange={updateBusy} />
    </div>}
  </div>
}
