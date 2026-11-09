import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Avatar } from '../profile/components/Avatar'
import { postTime } from '../posts/utils'
import { chatApi } from './api'
import { useChat } from './ChatProvider'
import type { ConversationPage } from './types'
import { chatError } from './utils'

export function ConversationList({ selected }: { selected?: string }) {
  const { revision, refresh } = useChat()
  const [data, setData] = useState<ConversationPage | null>(null)
  const [request, setRequest] = useState({ page: 0, attempt: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const loadedRevision = useRef(revision)
  const locked = useRef(false)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const page = loadedRevision.current === revision ? request.page : 0
    setLoading(true); locked.current = true; setError('')
    void chatApi.list(page, controller.signal).then(response => {
      if (!active) return
      loadedRevision.current = revision
      setData(previous => {
        if (!previous || page === 0) return response.data
        const ids = new Set(previous.items.map(item => item.id))
        return { ...response.data, items: [...previous.items, ...response.data.items.filter(item => !ids.has(item.id))] }
      })
    }).catch(cause => { if (active) setError(chatError(cause)) })
      .finally(() => { if (active) { setLoading(false); locked.current = false } })
    return () => { active = false; controller.abort() }
  }, [request, revision])

  return <aside className="chat-conversations" aria-label="Danh sách cuộc trò chuyện">
    <header className="chat-list-heading"><div><span className="eyebrow">KẾT NỐI MỖI NGÀY</span><h1>Tin nhắn</h1></div><button type="button" className="chat-text-button" disabled={loading} onClick={refresh}>Làm mới</button></header>
    <div className="chat-conversation-scroll">
      <ul className="chat-conversation-list">{data?.items.map(item => <li key={item.id}><Link to={`/messages/${encodeURIComponent(item.id)}`} className={`chat-conversation${item.id === selected ? ' selected' : ''}`} aria-current={item.id === selected ? 'page' : undefined}>
        <Avatar userId={item.otherUser.userId} name={item.otherUser.fullName} src={item.otherUser.avatarUrl} className="chat-avatar" />
        <span className="chat-conversation-copy"><strong>{item.otherUser.fullName}</strong><span>{item.lastMessage?.content || 'Bắt đầu cuộc trò chuyện'}</span><small>{item.lastMessage ? postTime(item.lastMessage.createdAt) : 'Chưa có tin nhắn'}</small></span>
        {item.unreadCount > 0 && <span className="chat-unread" aria-label={`${item.unreadCount} tin chưa đọc`}>{item.unreadCount > 99 ? '99+' : item.unreadCount}</span>}
      </Link></li>)}</ul>
      {loading && <p className="chat-state" role="status">Đang tải cuộc trò chuyện…</p>}
      {error && <p className="chat-error" role="alert">{error} <button type="button" onClick={refresh}>Thử lại</button></p>}
      {!loading && !error && data?.items.length === 0 && <div className="chat-empty"><h2>Chưa có cuộc trò chuyện</h2><p>Mở hồ sơ bạn bè và bấm Nhắn tin để bắt đầu.</p><Link to="/friends">Xem bạn bè →</Link></div>}
      {data?.hasNext && !error && <button className="chat-load-more" type="button" disabled={loading} onClick={() => { if (!locked.current) { locked.current = true; setRequest(current => ({ page: data.page + 1, attempt: current.attempt + 1 })) } }}>Xem thêm cuộc trò chuyện</button>}
    </div>
  </aside>
}
