import { useEffect, useId, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { profileApi } from '../api'
import type { Profile, ProfileImageType } from '../types'
import { profileError, validateImage } from '../utils'

export function ImageUploadDialog({ type, onDismiss, onSaved }: {
  type: ProfileImageType
  onDismiss: () => void
  onSaved: (profile: Profile) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const locked = useRef(false)
  const titleId = useId()
  const inputId = useId()
  const [selection, setSelection] = useState<{ file: File; url: string } | null>(null)
  const [ready, setReady] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const label = type === 'avatar' ? 'ảnh đại diện' : 'ảnh bìa'

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  useEffect(() => {
    return () => { if (selection) URL.revokeObjectURL(selection.url) }
  }, [selection])

  function chooseFile(file?: File) {
    if (!file) return
    setError('')
    try {
      validateImage(file)
      setReady(false)
      setSelection({ file, url: URL.createObjectURL(file) })
    } catch (cause) {
      setError(profileError(cause))
      setSelection(null)
      setReady(false)
    }
  }

  return <dialog ref={dialog} className="image-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!locked.current) onDismiss() }}>
    <div className="dialog-heading"><div><span className="eyebrow form-eyebrow">MỘT DIỆN MẠO MỚI</span><h2 id={titleId}>Thay {label}</h2></div><button className="icon-button" type="button" aria-label="Đóng hộp thoại" disabled={busy} onClick={onDismiss}><Icon name="close" /></button></div>
    <p className="dialog-description">Chọn ảnh JPEG, PNG hoặc WebP, tối đa 5 MB.</p>
    <input id={inputId} ref={input} className="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={event => {
      chooseFile(event.target.files?.[0])
      event.target.value = ''
    }} />
    {selection ? <div className={`image-preview preview-${type}`}>
      <img src={selection.url} alt={`Xem trước ${label}`} onLoad={() => setReady(true)} onError={() => { setReady(false); setError('Không thể đọc ảnh này. Hãy chọn một file ảnh khác.') }} />
    </div> : <div className="upload-placeholder"><Icon name="upload" width="34" height="34" /><strong>Chọn một tấm ảnh bạn yêu thích</strong><span>{type === 'avatar' ? 'Ảnh sẽ hiển thị trong khung tròn.' : 'Ảnh ngang sẽ phù hợp với khung ảnh bìa.'}</span></div>}
    {selection && <p className="selected-file">{selection.file.name}<span>{(selection.file.size / 1024 / 1024).toFixed(2)} MB</span></p>}
    <button type="button" className="button button-secondary choose-image-button" disabled={busy} onClick={() => input.current?.click()}><Icon name="camera" />{selection ? 'Chọn ảnh khác' : 'Chọn ảnh từ thiết bị'}</button>
    {error && <div className="feedback feedback-error" role="alert">{error}</div>}
    <p className="upload-note">Ảnh hiện tại chỉ được thay sau khi tải ảnh mới lên thành công.</p>
    <div className="profile-form-actions"><button type="button" className="button button-secondary" disabled={busy} onClick={onDismiss}>Hủy</button><button type="button" className="button button-primary" disabled={busy || !selection || !ready} onClick={async () => {
      if (!selection || !ready || locked.current) return
      locked.current = true
      setBusy(true)
      setError('')
      try {
        const response = await profileApi.upload(type, selection.file)
        onSaved(response.data)
      } catch (cause) { setError(profileError(cause)) }
      finally { locked.current = false; setBusy(false) }
    }}><Icon name="upload" />{busy ? 'Đang tải lên…' : 'Lưu ảnh mới'}</button></div>
  </dialog>
}
