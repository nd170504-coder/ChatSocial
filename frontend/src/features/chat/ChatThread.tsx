import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { Icon } from '../../components/ui/Icon'
import { useAuth } from '../auth/hooks/useAuth'
import { Avatar } from '../profile/components/Avatar'
import { validateImage } from '../profile/utils'
import { ChatImage, LocalChatImage } from './ChatImage'
import { useOnline } from '../presence/PresenceProvider'
import { postTime } from '../posts/utils'
import { chatApi } from './api'
import { useChat } from './ChatProvider'
import type { ChatMessage, ChatRead, Conversation } from './types'
import { applyRead, chatError, compareMessages, mergeMessages } from './utils'

type Pending = { clientMessageId: string; content: string; file?: File; status: 'sending' | 'failed'; error?: string }

export function ChatThread({ conversationId }: { conversationId: string }) {
  const { session } = useAuth()
  const userId = session!.user.id
  const { status, connectionRevision, subscribe, refresh, acknowledgeRead } = useChat()
  const [detail, setDetail] = useState<Conversation | null>(null)
  const online = useOnline(detail?.otherUser.userId)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [pending, setPending] = useState<Pending[]>([])
  const [draft, setDraft] = useState('')
  const [selectedImage, setSelectedImage] = useState<File | null>(null)
  const imageInput = useRef<HTMLInputElement>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sendError, setSendError] = useState('')
  const [readError, setReadError] = useState('')
  const [olderError, setOlderError] = useState('')
  const [busy, setBusy] = useState(false)
  const [olderBusy, setOlderBusy] = useState(false)
  const [before, setBefore] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [readCycle, setReadCycle] = useState(0)
  const [atBottom, setAtBottom] = useState(true)
  const [newMessages, setNewMessages] = useState(false)
  const scroll = useRef<HTMLDivElement>(null)
  const active = useRef(false), initialized = useRef(false), sending = useRef(false), reading = useRef(false), olderLocked = useRef(false)
  const messageRef = useRef<ChatMessage[]>([])
  const bottomRef = useRef(true)
  const prepend = useRef<{ height: number; top: number } | null>(null)
  const acknowledged = useRef(new Set<string>())
  const frontiers = useRef(new Map<string, ChatRead>())
  const lastReadAttempt = useRef<string | null>(null)
  const olderController = useRef<AbortController | null>(null)

  useEffect(() => { active.current = true; return () => { active.current = false; olderController.current?.abort() } }, [])
  useLayoutEffect(() => { messageRef.current = messages }, [messages])

  function ingest(incoming: ChatMessage[]) {
    for (const item of incoming) if (item.senderId === userId && item.clientMessageId) acknowledged.current.add(item.clientMessageId)
    setPending(previous => previous.filter(item => !acknowledged.current.has(item.clientMessageId)))
    setMessages(previous => {
      let merged = mergeMessages(previous, incoming)
      for (const read of frontiers.current.values()) merged = applyRead(merged, read)
      return merged
    })
  }

  useEffect(() => subscribe(event => {
    if (!active.current || event.conversationId !== conversationId) return
    if (event.type === 'MESSAGE_CREATED') {
      if (event.message.senderId === userId) bottomRef.current = true
      else if (!bottomRef.current) setNewMessages(true)
      ingest([event.message])
    } else {
      const read = event.read
      const previous = frontiers.current.get(read.readerId)
      if (read.lastReadMessageId && read.lastReadMessageCreatedAt && (!previous?.lastReadMessageId || !previous.lastReadMessageCreatedAt
        || compareMessages({ id: read.lastReadMessageId, createdAt: read.lastReadMessageCreatedAt }, { id: previous.lastReadMessageId, createdAt: previous.lastReadMessageCreatedAt }) >= 0)) {
        frontiers.current.set(read.readerId, read)
        setMessages(items => applyRead(items, read))
      }
    }
  }), [subscribe, conversationId, userId])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    const known = new Set(messageRef.current.map(item => item.id))
    const catchUp = initialized.current && known.size > 0
    setLoading(true); setError('')
    void (async () => {
      const [conversation, initial] = await Promise.all([chatApi.get(conversationId, controller.signal), chatApi.history(conversationId, null, controller.signal)])
      let page = initial.data
      const incoming = [...page.items]
      // Walk back to an already loaded message, so reconnects cannot leave a gap of >20 messages.
      const cursors = new Set<string>()
      while (catchUp && page.hasNext && page.nextBefore && !page.items.some(item => known.has(item.id))) {
        if (cursors.has(page.nextBefore)) throw new Error('Repeated cursor')
        cursors.add(page.nextBefore)
        page = (await chatApi.history(conversationId, page.nextBefore, controller.signal)).data
        incoming.push(...page.items)
      }
      if (!current) return
      setDetail(conversation.data)
      if (!initialized.current) { setBefore(initial.data.nextBefore); initialized.current = true; bottomRef.current = true }
      ingest(incoming)
      lastReadAttempt.current = null; setReadError('')
    })().catch(cause => { if (current) setError(chatError(cause)) })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false; controller.abort() }
  }, [conversationId, connectionRevision, attempt])

  useLayoutEffect(() => {
    const element = scroll.current
    if (!element) return
    if (prepend.current) {
      element.scrollTop = prepend.current.top + element.scrollHeight - prepend.current.height
      prepend.current = null
    } else if (bottomRef.current) element.scrollTop = element.scrollHeight
  }, [messages, pending])

  useEffect(() => {
    if (loading || !atBottom || document.visibilityState !== 'visible' || reading.current) return
    const latest = [...messages].reverse().find(item => item.senderId !== userId && !item.readByRecipient)
    if (!latest || lastReadAttempt.current === latest.id) return
    lastReadAttempt.current = latest.id; reading.current = true
    void chatApi.read(conversationId, latest.id).then(response => {
      if (!active.current) return
      frontiers.current.set(response.data.readerId, response.data)
      setMessages(items => applyRead(items, response.data)); setReadError(''); acknowledgeRead(conversationId, response.data.unreadCount); refresh()
      setReadCycle(value => value + 1)
    }).catch(cause => { if (active.current) setReadError(chatError(cause)) })
      .finally(() => { reading.current = false; if (active.current) setReadCycle(value => value + 1) })
  }, [messages, loading, atBottom, readCycle, conversationId, userId, refresh, acknowledgeRead])

  async function loadOlder() {
    if (!before || olderLocked.current || loading) return
    olderLocked.current = true; setOlderBusy(true); setOlderError('')
    const controller = new AbortController(); olderController.current = controller
    try {
      const response = await chatApi.history(conversationId, before, controller.signal)
      if (!active.current) return
      if (scroll.current) prepend.current = { height: scroll.current.scrollHeight, top: scroll.current.scrollTop }
      ingest(response.data.items); setBefore(response.data.nextBefore)
    } catch (cause) { if (active.current) setOlderError(chatError(cause)) }
    finally { olderLocked.current = false; if (active.current) setOlderBusy(false) }
  }

  async function send(item: Pending) {
    if (sending.current || !detail?.canSend) return
    sending.current = true; setBusy(true); setSendError('')
    setPending(previous => [...previous.filter(previousItem => previousItem.clientMessageId !== item.clientMessageId), { ...item, status: 'sending', error: undefined }])
    bottomRef.current = true; setAtBottom(true)
    try {
      const response = item.file
        ? await chatApi.sendImage(conversationId, item.file, item.clientMessageId)
        : await chatApi.send(conversationId, item.content, item.clientMessageId)
      if (active.current) { ingest([response.data]); refresh() }
    } catch (cause) {
      if (!active.current || acknowledged.current.has(item.clientMessageId)) return
      const message = chatError(cause)
      setPending(previous => previous.map(previousItem => previousItem.clientMessageId === item.clientMessageId ? { ...previousItem, status: 'failed', error: message } : previousItem))
      if (cause instanceof ApiError && cause.status === 403) { setDetail(previous => previous ? { ...previous, canSend: false } : previous); setSendError(message) }
    } finally { sending.current = false; if (active.current) setBusy(false) }
  }

  function submit() {
    const content = draft.trim()
    if ((!selectedImage && (!content || content.length > 2000)) || sending.current || !detail?.canSend || error) return
    const item: Pending = { content: selectedImage ? '' : content, file: selectedImage || undefined, clientMessageId: crypto.randomUUID(), status: 'sending' }
    if (selectedImage) setSelectedImage(null)
    else setDraft('')
    void send(item)
  }

  return <section className="chat-thread" aria-label="Nội dung cuộc trò chuyện">
    <header className="chat-thread-heading"><Link to="/messages" className="chat-mobile-back" aria-label="Về danh sách tin nhắn"><Icon name="arrow" /></Link>
      {detail ? <Link className="chat-person" to={`/users/${encodeURIComponent(detail.otherUser.userId)}`}><Avatar userId={detail.otherUser.userId} name={detail.otherUser.fullName} src={detail.otherUser.avatarUrl} className="chat-avatar" /><span><strong>{detail.otherUser.fullName}</strong>{online && <small className="chat-person-online" role="status">Đang hoạt động</small>}</span></Link> : <strong>Cuộc trò chuyện</strong>}
      <button type="button" className="chat-text-button" disabled={loading} onClick={() => setAttempt(value => value + 1)}>Làm mới</button>
    </header>
    <div className={`chat-connection ${status}`} role="status">{status === 'connected' ? 'Đã kết nối' : status === 'connecting' ? 'Đang kết nối…' : 'Đang kết nối lại; tin đã lưu sẽ được tải bù.'}</div>
    {error && <p className="chat-error" role="alert">{error} <button type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button></p>}
    <div ref={scroll} className="chat-history" aria-label="Lịch sử tin nhắn" aria-busy={loading || olderBusy} onScroll={event => {
      const element = event.currentTarget
      const bottom = element.scrollHeight - element.scrollTop - element.clientHeight < 64
      bottomRef.current = bottom; setAtBottom(bottom)
      if (bottom) setNewMessages(false)
    }}>
      {before && <button className="chat-load-more" type="button" disabled={loading || olderBusy} onClick={() => void loadOlder()}>{olderBusy ? 'Đang tải…' : 'Xem tin nhắn cũ hơn'}</button>}
      {olderError && <p className="chat-error" role="alert">{olderError}</p>}
      {loading && <p className="chat-state" role="status">Đang tải tin nhắn…</p>}
      {!loading && !error && messages.length === 0 && <div className="chat-empty"><Icon name="comment" width="36" height="36" /><h2>Bắt đầu câu chuyện</h2><p>Gửi một lời chào để kết nối với người bạn này.</p></div>}
      <ol className="chat-message-list">{messages.map(message => <li key={message.id} className={`chat-message ${message.senderId === userId ? 'own' : 'received'}`}>
        {message.content && <p>{message.content}</p>}
        {message.media?.filter(media => media.mediaType === 'IMAGE').map(media => <ChatImage key={media.id} conversationId={conversationId} messageId={message.id} mediaId={media.id} />)}
        <span><time dateTime={message.createdAt}>{postTime(message.createdAt)}</time>{message.senderId === userId && <span>{message.readByRecipient ? 'Đã đọc' : 'Đã gửi'}</span>}</span>
      </li>)}{pending.map(item => <li key={item.clientMessageId} className={`chat-message own pending ${item.status}`}>
        {item.file ? <LocalChatImage file={item.file} /> : <p>{item.content}</p>}
        <span>{item.status === 'sending' ? 'Đang gửi…' : 'Chưa gửi được'}</span>{item.status === 'failed' && <div className="chat-pending-error"><span>{item.error}</span><button type="button" disabled={busy || !detail?.canSend} onClick={() => void send(item)} aria-label={item.file ? 'Gửi lại ảnh' : `Gửi lại tin: ${item.content.slice(0, 30)}`}>Gửi lại</button><button type="button" disabled={busy} onClick={() => setPending(items => items.filter(previous => previous.clientMessageId !== item.clientMessageId))}>Bỏ tin chưa gửi</button></div>}</li>)}</ol>
    </div>
    {newMessages && <button className="chat-new-messages" type="button" onClick={() => { bottomRef.current = true; setAtBottom(true); setNewMessages(false); if (scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight }}>Tin nhắn mới ↓</button>}
    {readError && <p className="chat-error" role="alert">Chưa cập nhật được trạng thái đọc. <button type="button" onClick={() => { lastReadAttempt.current = null; setReadCycle(value => value + 1) }}>Thử lại</button></p>}
    {sendError && <p className="chat-error" role="alert">{sendError}</p>}
    {detail && !detail.canSend && <p className="chat-readonly" role="status">Hiện không thể gửi tin. Lịch sử cuộc trò chuyện vẫn được giữ lại.</p>}
    <form className="chat-composer" onSubmit={event => { event.preventDefault(); submit() }}>
      <input ref={imageInput} type="file" className="chat-input-label" tabIndex={-1} aria-label="Chọn ảnh gửi trong chat" accept="image/jpeg,image/png,image/webp" disabled={busy || !detail?.canSend || !!error} onChange={event => {
        const file = event.target.files?.[0]
        event.target.value = ''
        if (!file) return
        try { validateImage(file); setSelectedImage(file); setSendError('') }
        catch (cause) { setSelectedImage(null); setSendError(cause instanceof Error ? cause.message : 'Ảnh không hợp lệ.') }
      }} />
      {selectedImage && <div className="chat-selected-image"><LocalChatImage file={selectedImage} /><span>{selectedImage.name}<small>Ảnh sẽ được gửi thành một tin riêng.</small></span><button type="button" className="icon-button" aria-label="Bỏ ảnh đã chọn" disabled={busy} onClick={() => setSelectedImage(null)}><Icon name="close" /></button></div>}
      <label htmlFor={`chat-input-${conversationId}`} className="chat-input-label">Tin nhắn</label>
      <textarea id={`chat-input-${conversationId}`} rows={2} maxLength={2000} value={draft} disabled={busy || !!selectedImage || !detail?.canSend || !!error} placeholder={detail?.canSend ? 'Nhập tin nhắn…' : 'Không thể gửi tin lúc này'} onChange={event => setDraft(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submit() }
      }} />
      <div><button type="button" className="chat-attach" aria-label="Thêm ảnh vào tin nhắn" title="JPEG, PNG, WebP · tối đa 5 MB" disabled={busy || !detail?.canSend || !!error} onClick={() => imageInput.current?.click()}><Icon name="camera" />Thêm ảnh</button><span>{draft.length.toLocaleString('vi-VN')}/2.000</span><button type="submit" className="chat-send" aria-label={selectedImage ? 'Gửi ảnh' : 'Gửi tin nhắn'} disabled={busy || (!selectedImage && !draft.trim()) || !detail?.canSend || !!error}><Icon name="send" /></button></div>
    </form>
  </section>
}
