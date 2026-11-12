import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { blocksApi, type BlockStatus } from './api'
import { RecallPopover } from '../chat/RecallPopover'
import { useChat } from '../chat/ChatProvider'
import './blocks.css'

export function BlockButton({ userId, fullName, initiallyBlocked, onUpdated }: { userId: string; fullName: string; initiallyBlocked?: boolean; onUpdated?: () => void }) {
  const { relationshipsChanged } = useChat()
  const [status, setStatus] = useState<BlockStatus | null>(initiallyBlocked === undefined ? null : { userId, blockedByMe: initiallyBlocked, interactionBlocked: initiallyBlocked })
  const [anchor, setAnchor] = useState<HTMLButtonElement | null>(null)
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [attempt, setAttempt] = useState(0)
  const active = useRef(false), locked = useRef(false), id = useId()
  const cancel = useCallback(() => setAnchor(null), [])
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  useEffect(() => {
    if (initiallyBlocked !== undefined) return
    let current = true
    const controller = new AbortController()
    void blocksApi.status(userId, controller.signal).then(response => { if (current) { setStatus(response.data); setError('') } })
      .catch(() => { if (current) setError('Chưa tải được trạng thái chặn.') })
    return () => { current = false; controller.abort() }
  }, [userId, attempt, initiallyBlocked])

  async function confirm() {
    if (locked.current || !status) return
    locked.current = true; setBusy(true); setError('')
    try {
      await blocksApi.change(userId, !status.blockedByMe)
      if (active.current) { setAnchor(null); onUpdated?.(); relationshipsChanged() }
    } catch { if (active.current) setError('Chưa cập nhật được. Vui lòng thử lại.') }
    finally { locked.current = false; if (active.current) setBusy(false) }
  }
  return <div className="block-action">
    {!status ? error ? <button className="block-button" type="button" onClick={() => { setError(''); setAttempt(value => value + 1) }}>Thử lại trạng thái chặn</button> : <span className="block-loading">Đang tải…</span>
      : <button className="block-button" type="button" disabled={busy} aria-haspopup="dialog" aria-expanded={!!anchor} aria-controls={anchor ? id : undefined}
        onClick={event => { setError(''); setAnchor(anchor ? null : event.currentTarget) }}>{status.blockedByMe ? 'Bỏ chặn' : 'Chặn'}</button>}
    {anchor && status && <RecallPopover id={id} anchor={anchor} busy={busy} error={error} onCancel={cancel} onConfirm={() => void confirm()}
      title={`${status.blockedByMe ? 'Bỏ chặn' : 'Chặn'} ${fullName}?`}
      description={status.blockedByMe ? 'Bỏ chặn không tự khôi phục kết bạn.' : 'Hai người sẽ hủy kết bạn và không thể liên hệ hoặc xem bài viết của nhau.'}
      confirmLabel={status.blockedByMe ? 'Bỏ chặn' : 'Chặn'} confirmAriaLabel={status.blockedByMe ? 'Xác nhận bỏ chặn' : 'Xác nhận chặn'} />}
  </div>
}
