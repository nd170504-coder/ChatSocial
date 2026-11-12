import { authenticatedBlobRequest, authenticatedRequest } from '../auth/session'
import type { ChatMessage, ChatRead, Conversation, ConversationPage, MessagePage } from './types'

export const chatApi = {
  recall: (id: string, messageId: string) => authenticatedRequest<ChatMessage>(
    `/conversations/${encodeURIComponent(id)}/messages/${encodeURIComponent(messageId)}`, { method: 'DELETE', signal: AbortSignal.timeout(20_000) },
  ),
  open: (userId: string) => authenticatedRequest<Conversation>('/conversations/direct', { method: 'POST', body: JSON.stringify({ userId }) }),
  list: (page = 0, signal?: AbortSignal) => authenticatedRequest<ConversationPage>(`/conversations?page=${page}&size=20`, { signal }),
  get: (id: string, signal?: AbortSignal) => authenticatedRequest<Conversation>(`/conversations/${encodeURIComponent(id)}`, { signal }),
  history: (id: string, before?: string | null, signal?: AbortSignal) => authenticatedRequest<MessagePage>(
    `/conversations/${encodeURIComponent(id)}/messages?size=20${before ? `&before=${encodeURIComponent(before)}` : ''}`, { signal },
  ),
  send: (id: string, content: string, clientMessageId: string) => authenticatedRequest<ChatMessage>(
    `/conversations/${encodeURIComponent(id)}/messages`, { method: 'POST', body: JSON.stringify({ content, clientMessageId }), signal: AbortSignal.timeout(20_000) },
  ),
  read: (id: string, lastReadMessageId: string) => authenticatedRequest<ChatRead>(
    `/conversations/${encodeURIComponent(id)}/read`, { method: 'PUT', body: JSON.stringify({ lastReadMessageId }) },
  ),
  sendImage: (id: string, file: File, clientMessageId: string) => {
    const body = new FormData()
    body.append('clientMessageId', clientMessageId)
    body.append('file', file)
    return authenticatedRequest<ChatMessage>(`/conversations/${encodeURIComponent(id)}/images`, {
      method: 'POST', body, signal: AbortSignal.timeout(60_000),
    })
  },
  image: (conversationId: string, messageId: string, mediaId: string, signal?: AbortSignal) => authenticatedBlobRequest(
    `/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/media/${encodeURIComponent(mediaId)}`, { signal },
  ),
}
