import { ApiError } from '../../api/client'
import type { ChatEvent, ChatMessage, ChatRead } from './types'

function dateKey(value: string) {
  const parts = value.match(/^(.*?)(?:\.(\d+))?Z$/)
  return parts ? `${parts[1]}.${(parts[2] || '').padEnd(9, '0')}Z` : value
}
export function compareMessages(a: Pick<ChatMessage, 'createdAt' | 'id'>, b: Pick<ChatMessage, 'createdAt' | 'id'>) {
  const left = dateKey(a.createdAt), right = dateKey(b.createdAt)
  return left < right ? -1 : left > right ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0
}
export function mergeMessages(previous: ChatMessage[], incoming: ChatMessage[]) {
  const byId = new Map(previous.map(item => [item.id, item]))
  for (const item of incoming) byId.set(item.id, { ...item, readByRecipient: item.readByRecipient || byId.get(item.id)?.readByRecipient || false })
  const items = [...byId.values()].sort(compareMessages)
  const seen = new Map<string, ChatMessage>()
  for (const item of items) if (item.readByRecipient) seen.set(item.senderId, item)
  return items.map(item => seen.has(item.senderId) && compareMessages(item, seen.get(item.senderId)!) <= 0 ? { ...item, readByRecipient: true } : item)
}
export function applyRead(messages: ChatMessage[], read: ChatRead) {
  if (!read.lastReadMessageId || !read.lastReadMessageCreatedAt) return messages
  const through = { id: read.lastReadMessageId, createdAt: read.lastReadMessageCreatedAt }
  return messages.map(item => item.conversationId === read.conversationId && item.senderId !== read.readerId && compareMessages(item, through) <= 0
    ? { ...item, readByRecipient: true } : item)
}
function validMessage(item: ChatMessage) {
  return item && typeof item.id === 'string' && typeof item.conversationId === 'string' && typeof item.senderId === 'string'
    && typeof item.content === 'string' && typeof item.createdAt === 'string' && Number.isFinite(Date.parse(item.createdAt))
    && (item.clientMessageId === null || typeof item.clientMessageId === 'string') && typeof item.readByRecipient === 'boolean'
}
export function parseChatEvent(body: string): ChatEvent | null {
  try {
    const event = JSON.parse(body)
    if (typeof event?.conversationId !== 'string') return null
    if (event.type === 'MESSAGE_CREATED' && validMessage(event.message) && event.message.conversationId === event.conversationId) return event
    const read = event.read
    if (event.type === 'MESSAGES_READ' && read?.conversationId === event.conversationId && typeof read.readerId === 'string'
      && typeof read.unreadCount === 'number' && ((read.lastReadMessageId === null && read.lastReadMessageCreatedAt === null)
        || (typeof read.lastReadMessageId === 'string' && typeof read.lastReadMessageCreatedAt === 'string' && Number.isFinite(Date.parse(read.lastReadMessageCreatedAt))))) return event
  } catch { /* Ignore malformed events. */ }
  return null
}
export function chatError(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (cause.status === 403) return 'Hai người hiện không thể gửi tin cho nhau. Bạn vẫn có thể xem lịch sử.'
    if (cause.status === 404) return 'Cuộc trò chuyện hoặc tin nhắn không còn khả dụng.'
    if (cause.status === 409) return 'Mã gửi tin đã được sử dụng cho nội dung khác.'
    if (cause.status === 400) return 'Hãy kiểm tra nội dung tin nhắn, tối đa 2.000 ký tự.'
  }
  return 'Chưa thể kết nối máy chủ. Vui lòng thử lại.'
}
