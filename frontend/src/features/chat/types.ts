export interface ChatMedia { id: string; url: string; mediaType: 'IMAGE' | 'VIDEO' }
export interface ChatMessage {
  id: string
  conversationId: string
  senderId: string
  content: string | null
  media: ChatMedia[]
  clientMessageId: string | null
  createdAt: string
  readByRecipient: boolean
  deletedAt: string | null
}
export interface Conversation {
  id: string
  otherUser: { userId: string; username: string; fullName: string; avatarUrl: string | null }
  lastMessage: ChatMessage | null
  unreadCount: number
  canSend: boolean
  updatedAt: string
}
export interface ConversationPage { items: Conversation[]; page: number; size: number; hasNext: boolean }
export interface MessagePage { items: ChatMessage[]; hasNext: boolean; nextBefore: string | null }
export interface ChatRead {
  conversationId: string; readerId: string; lastReadMessageId: string | null
  lastReadMessageCreatedAt: string | null; unreadCount: number
}
export type ChatEvent = { type: 'MESSAGE_CREATED' | 'MESSAGE_RECALLED'; conversationId: string; message: ChatMessage; read: null }
  | { type: 'RELATIONSHIP_CHANGED'; conversationId: string; message: null; read: null }
  | { type: 'MESSAGES_READ'; conversationId: string; message: null; read: ChatRead }
