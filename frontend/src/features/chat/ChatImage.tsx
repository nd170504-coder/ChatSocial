import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from '../../components/ui/Icon'
import { chatApi } from './api'

function ImageViewer({ src, onClose }: { src: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])
  return createPortal(<dialog ref={dialog} className="chat-image-viewer" aria-label="Ảnh trong cuộc trò chuyện"
    onCancel={event => { event.preventDefault(); onClose() }}
    onClick={event => { if (event.target === event.currentTarget) onClose() }}>
    <header><strong>Ảnh trong cuộc trò chuyện</strong><button type="button" className="icon-button" aria-label="Đóng ảnh" onClick={onClose}><Icon name="close" /></button></header>
    <img src={src} alt="Ảnh trong cuộc trò chuyện được phóng to" />
  </dialog>, document.body)
}

export function LocalChatImage({ file }: { file: File }) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    const src = URL.createObjectURL(file)
    setUrl(src)
    return () => URL.revokeObjectURL(src)
  }, [file])
  return url ? <img className="chat-local-image" src={url} alt={`Ảnh đã chọn: ${file.name}`} /> : null
}

export function ChatImage({ conversationId, messageId, mediaId }: { conversationId: string; messageId: string; mediaId: string }) {
  const [url, setUrl] = useState(''), [error, setError] = useState(false), [attempt, setAttempt] = useState(0), [open, setOpen] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    let active = true, objectUrl: string | undefined
    setUrl(''); setError(false)
    void chatApi.image(conversationId, messageId, mediaId, controller.signal).then(blob => {
      if (!active) return
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(blob.type)) throw new Error('Invalid image response')
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch(() => { if (active) setError(true) })
    return () => { active = false; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [conversationId, messageId, mediaId, attempt])
  return <div className="chat-image">
    {error ? <div className="chat-image-state"><span>Chưa tải được ảnh</span><button type="button" onClick={() => setAttempt(value => value + 1)}>Thử tải lại ảnh</button></div>
      : url ? <button className="chat-image-open" type="button" aria-label="Xem ảnh trong tin nhắn" onClick={() => setOpen(true)}><img src={url} alt="Ảnh trong tin nhắn" onError={() => { setOpen(false); setError(true) }} /></button>
        : <span className="chat-image-state" role="status">Đang tải ảnh…</span>}
    {open && url && <ImageViewer src={url} onClose={() => setOpen(false)} />}
  </div>
}
