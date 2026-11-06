import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { Avatar } from '../../profile/components/Avatar'
import type { Post, PostPrivacy, PostValues } from '../types'
import { buildPostValues, postError, privacyLabels, validatePostFiles } from '../utils'

function FilePreview({ file, onRemove }: { file: File; onRemove: () => void }) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    const objectUrl = URL.createObjectURL(file)
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file])
  return <div className="post-file-preview"><img src={url || undefined} alt={file.name} /><button type="button" className="icon-button" aria-label={`Bỏ ảnh ${file.name}`} onClick={onRemove}><Icon name="close" /></button><span title={file.name}>{file.name}</span></div>
}

export function PostEditor({ name, avatarUrl, initial, defaultPrivacy = 'FRIENDS', onSave, onCancel, onBusyChange }: {
  name: string
  avatarUrl?: string | null
  initial?: Post
  defaultPrivacy?: PostPrivacy
  onSave: (values: PostValues, files: File[]) => Promise<void>
  onCancel?: () => void
  onBusyChange?: (busy: boolean) => void
}) {
  const [content, setContent] = useState(initial?.content || '')
  const [privacy, setPrivacy] = useState<PostPrivacy>(initial?.privacy || defaultPrivacy)
  const [files, setFiles] = useState<File[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const locked = useRef(false)
  const input = useRef<HTMLInputElement>(null)
  const id = useId()

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (locked.current) return
    setError('')
    try {
      validatePostFiles(files)
      const values = buildPostValues(content, privacy, initial ? initial.media.length : files.length)
      locked.current = true
      setBusy(true)
      onBusyChange?.(true)
      await onSave(values, files)
      if (!initial) { setContent(''); setFiles([]) }
    } catch (cause) { setError(postError(cause)) }
    finally { locked.current = false; setBusy(false); onBusyChange?.(false) }
  }

  return <form className="post-editor" onSubmit={submit}>
    <fieldset disabled={busy}>
      <div className="post-editor-identity"><Avatar name={name} src={avatarUrl} className="post-avatar" /><div><strong>{name}</strong><label className="post-privacy-select"><span className="visually-hidden">Ai có thể xem bài viết?</span><Icon name="shield" width="14" height="14" /><select value={privacy} onChange={event => setPrivacy(event.target.value as PostPrivacy)}>{Object.entries(privacyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div></div>
      <label className="visually-hidden" htmlFor={`${id}-content`}>Nội dung bài viết</label>
      <textarea id={`${id}-content`} className="post-textarea" value={content} maxLength={5000} rows={4} placeholder="Hôm nay bạn có điều gì muốn chia sẻ?" onChange={event => setContent(event.target.value)} />
      <div className="post-editor-meta"><span>{initial ? 'Bạn có thể sửa nội dung và quyền riêng tư.' : 'Một câu chuyện nhỏ cũng đáng để sẻ chia.'}</span><span>{content.length.toLocaleString('vi-VN')}/5.000</span></div>
      {!initial && <>
        {files.length > 0 && <div className="post-file-grid">{files.map((file, index) => <FilePreview key={`${file.name}-${file.lastModified}-${index}`} file={file} onRemove={() => setFiles(current => current.filter((_, itemIndex) => itemIndex !== index))} />)}</div>}
        <input ref={input} className="visually-hidden" type="file" multiple accept="image/jpeg,image/png,image/webp" aria-label="Chọn ảnh cho bài viết" onChange={event => {
          const next = [...files, ...Array.from(event.target.files || [])]
          event.target.value = ''
          try { validatePostFiles(next); setFiles(next); setError('') } catch (cause) { setError(postError(cause)) }
        }} />
      </>}
      {initial && initial.media.length > 0 && <p className="post-helper">{initial.media.length} ảnh đính kèm được giữ nguyên.</p>}
      {error && <div className="feedback feedback-error" role="alert">{error}</div>}
      <div className="post-editor-actions">
        {!initial && <button type="button" className="post-add-image" disabled={files.length >= 5} onClick={() => input.current?.click()}><Icon name="camera" /><span>Thêm ảnh <small>{files.length}/5 · tối đa 5 MB/ảnh</small></span></button>}
        <div className="post-editor-submit">{onCancel && <button className="button button-secondary" type="button" onClick={onCancel}>Hủy</button>}<button className="button button-primary" type="submit" disabled={busy || (!content.trim() && (initial ? initial.media.length : files.length) === 0)}>{busy ? (initial ? 'Đang lưu…' : 'Đang đăng…') : initial ? 'Lưu thay đổi' : 'Đăng bài'}<Icon className={initial ? undefined : 'post-send-icon'} name={initial ? 'check' : 'arrow'} /></button></div>
      </div>
    </fieldset>
  </form>
}
