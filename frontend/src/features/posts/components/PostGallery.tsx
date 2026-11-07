import { useEffect, useId, useRef, useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import type { PostMedia } from '../types'
import { PostImage } from './PostImage'

function ImageViewer({ postId, images, initialIndex, onClose }: {
  postId: string; images: PostMedia[]; initialIndex: number; onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const [index, setIndex] = useState(initialIndex)
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  function close() {
    dialog.current?.close()
    onClose()
  }

  return <dialog ref={dialog} className="post-image-viewer" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close() }}
    onClick={event => { if (event.target === event.currentTarget) close() }}
    onKeyDown={event => {
      if (event.key === 'ArrowLeft') { event.preventDefault(); setIndex(value => Math.max(0, value - 1)) }
      if (event.key === 'ArrowRight') { event.preventDefault(); setIndex(value => Math.min(images.length - 1, value + 1)) }
    }}>
    <div className="dialog-heading"><h2 id={titleId} aria-live="polite">Ảnh {index + 1} / {images.length}</h2>
      <button className="icon-button" type="button" aria-label="Đóng trình xem ảnh" onClick={close}><Icon name="close" /></button>
    </div>
    <PostImage key={images[index].id} postId={postId} mediaId={images[index].id} index={index} />
    {images.length > 1 && <nav className="post-image-navigation" aria-label="Chuyển ảnh">
      <button type="button" className="button button-secondary" disabled={index === 0} onClick={() => setIndex(value => value - 1)}><Icon name="arrow" />Ảnh trước</button>
      <button type="button" className="button button-secondary" disabled={index === images.length - 1} onClick={() => setIndex(value => value + 1)}>Ảnh sau<Icon className="post-image-next" name="arrow" /></button>
    </nav>}
  </dialog>
}

export function PostGallery({ postId, media }: { postId: string; media: PostMedia[] }) {
  const [selected, setSelected] = useState<number | null>(null)
  const images = media.filter(item => item.mediaType === 'IMAGE').sort((a, b) => a.displayOrder - b.displayOrder)
  if (images.length === 0) return null
  const visible = images.slice(0, 3)
  return <>
    <div className={`post-media-grid post-media-${visible.length}`}>
      {visible.map((image, index) => <PostImage key={image.id} postId={postId} mediaId={image.id} index={index}
        moreCount={index === 2 ? images.length - visible.length : 0} onOpen={() => setSelected(index)} />)}
    </div>
    {selected !== null && <ImageViewer postId={postId} images={images} initialIndex={selected} onClose={() => setSelected(null)} />}
  </>
}
