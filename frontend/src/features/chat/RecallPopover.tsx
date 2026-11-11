import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'

export function RecallPopover({ id, anchor, busy, error, onCancel, onConfirm }: {
  id: string; anchor: HTMLButtonElement; busy: boolean; error: string
  onCancel: () => void; onConfirm: () => void
}) {
  const panel = useRef<HTMLDivElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const [position, setPosition] = useState<{ left: number; top: number; side: string; arrow: number } | null>(null)
  const positioned = position !== null

  useEffect(() => {
    if (positioned) cancel.current?.focus({ preventScroll: true })
  }, [positioned])

  useLayoutEffect(() => {
    const update = () => {
      if (!panel.current) return
      const target = anchor.getBoundingClientRect(), box = panel.current.getBoundingClientRect()
      const clamp = (value: number, max: number) => Math.max(12, Math.min(value, max))
      let left: number, top: number, side: string, arrow: number
      if (target.left >= box.width + 24) {
        left = target.left - box.width - 12
        top = clamp(target.bottom - box.height + 8, window.innerHeight - box.height - 12)
        side = 'left'; arrow = clamp(target.top + target.height / 2 - top, box.height - 16)
      } else {
        left = clamp(target.right - box.width, window.innerWidth - box.width - 12)
        const above = target.top >= box.height + 24
        top = clamp(above ? target.top - box.height - 12 : target.bottom + 12, window.innerHeight - box.height - 12)
        side = above ? 'above' : 'below'; arrow = clamp(target.left + target.width / 2 - left, box.width - 16)
      }
      setPosition(previous => previous?.left === left && previous.top === top && previous.side === side && previous.arrow === arrow
        ? previous : { left, top, side, arrow })
    }
    update()
    const observer = new ResizeObserver(update)
    if (panel.current) observer.observe(panel.current)
    observer.observe(anchor)
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update, true)
      if (anchor.isConnected) anchor.focus({ preventScroll: true })
    }
  }, [anchor])

  useEffect(() => {
    const outside = (event: PointerEvent) => {
      if (!busy && !panel.current?.contains(event.target as Node) && !anchor.contains(event.target as Node)) onCancel()
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) { event.preventDefault(); onCancel() }
    }
    document.addEventListener('pointerdown', outside, true)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', outside, true); document.removeEventListener('keydown', escape) }
  }, [anchor, busy, onCancel])

  const style = { left: position?.left ?? 0, top: position?.top ?? 0, visibility: position ? 'visible' : 'hidden', '--recall-arrow': `${position?.arrow ?? 20}px` } as CSSProperties
  return createPortal(<div ref={panel} id={id} className={`chat-recall-popover ${position?.side || ''}`} style={style}
    role="dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} aria-busy={busy}>
    <strong id={`${id}-title`}>Thu hồi tin nhắn?</strong>
    <p id={`${id}-description`}>Thu hồi với cả hai người.</p>
    {error && <p className="chat-recall-error" role="alert">{error}</p>}
    <div className="chat-recall-actions"><button ref={cancel} type="button" disabled={busy} onClick={onCancel}>Hủy</button><button className="chat-recall-primary" type="button" disabled={busy} aria-label="Xác nhận thu hồi" onClick={onConfirm}>{busy ? 'Đang thu hồi…' : 'Thu hồi'}</button></div>
  </div>, document.body)
}
