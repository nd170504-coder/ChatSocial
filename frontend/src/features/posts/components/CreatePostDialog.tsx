import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from '../../../components/ui/Icon'
import { postApi } from '../api'
import { PostEditor } from './PostEditor'

export function CreatePostDialog({ name, avatarUrl, onClose, onCreated }: {
  name: string; avatarUrl?: string | null; onClose: () => void; onCreated: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const locked = useRef(false)
  const [busy, setBusy] = useState(false)
  const titleId = useId()

  useEffect(() => {
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    element?.querySelector('textarea')?.focus({ preventScroll: true })
    return () => { element?.close(); document.body.style.overflow = previousOverflow }
  }, [])

  function close() { if (!locked.current) onClose() }

  return createPortal(<dialog ref={dialog} className="create-post-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close() }}
    onClick={event => { if (event.target === event.currentTarget) close() }}>
    <header className="create-post-heading"><div><span className="eyebrow">CHIA SẺ CÂU CHUYỆN</span><h2 id={titleId}>Tạo bài viết</h2></div>
      <button className="icon-button" type="button" aria-label="Đóng cửa sổ đăng bài" disabled={busy} onClick={close}><Icon name="close" /></button>
    </header>
    <PostEditor name={name} avatarUrl={avatarUrl} onCancel={close}
      onBusyChange={value => { locked.current = value; setBusy(value) }}
      onSave={async (values, files) => {
        await postApi.create(values, files)
        onCreated()
      }} />
  </dialog>, document.body)
}
