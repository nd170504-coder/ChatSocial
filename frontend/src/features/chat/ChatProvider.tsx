import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { env } from '../../config/env'
import { authStore } from '../auth/session'
import { connectUserChannel, type ConnectionStatus } from '../notifications/socket'
import { notificationSocketUrl } from '../notifications/utils'
import type { ChatEvent } from './types'
import { parseChatEvent } from './utils'
import { chatApi } from './api'
import { parseTypingEvent, type TypingEvent } from './typing'

const ChatContext = createContext<{
  status: ConnectionStatus; revision: number; connectionRevision: number
  relationshipRevision: number; relationshipsChanged: () => void
  subscribe: (listener: (event: ChatEvent) => void) => () => void
  subscribeTyping: (listener: (event: TypingEvent) => void) => () => void
  sendTyping: (conversationId: string, typing: boolean) => boolean
  refresh: () => void
  hasNewMessages: boolean
  acknowledgeRead: (id: string, unread: number) => void
} | null>(null)

export function ChatProvider({ userId, children }: { userId: string; children: ReactNode }) {
  const [relationshipRevision, setRelationshipRevision] = useState(0)
  const [status, setStatus] = useState<ConnectionStatus>('connecting')
  const [revision, setRevision] = useState(0)
  const [connectionRevision, setConnectionRevision] = useState(0)
  const [newConversations, setNewConversations] = useState(new Set<string>())
  const relationshipsChanged = useCallback(() => { setRelationshipRevision(value => value + 1); setRevision(value => value + 1); setNewConversations(new Set()) }, [])
  const listeners = useRef(new Set<(event: ChatEvent) => void>())
  const typingListeners = useRef(new Set<(event: TypingEvent) => void>())
  const socket = useRef<ReturnType<typeof connectUserChannel> | null>(null)
  const sendTyping = useCallback((conversationId: string, typing: boolean) => socket.current?.sendTyping(conversationId, typing) ?? false, [])
  const subscribeTyping = useCallback((listener: (event: TypingEvent) => void) => {
    typingListeners.current.add(listener)
    return () => { typingListeners.current.delete(listener) }
  }, [])
  const refresh = useCallback(() => setRevision(value => value + 1), [])
  const acknowledgeRead = useCallback((id: string, unread: number) => {
    setNewConversations(previous => { const next = new Set(previous); if (unread > 0) next.add(id); else next.delete(id); return next })
  }, [])
  const subscribe = useCallback((listener: (event: ChatEvent) => void) => {
    listeners.current.add(listener)
    return () => { listeners.current.delete(listener) }
  }, [])
  useEffect(() => {
    let active = true
    const versions = new Map<string, number>()
    const connection = connectUserChannel({
      url: notificationSocketUrl(env.apiBaseUrl, window.location.href), destination: '/user/queue/chat',
      getToken: async () => {
        const token = await authStore.getAccessToken()
        if (!active || authStore.getSnapshot().session?.user.id !== userId) throw new Error('Session changed')
        return token
      },
      onReady: () => { if (active) { refresh(); setConnectionRevision(value => value + 1) } },
      onStatus: value => { if (active) setStatus(value) },
      onTyping: body => {
        if (!active) return
        const event = parseTypingEvent(body)
        if (event && event.userId !== userId) for (const listener of typingListeners.current) listener(event)
      },
      onMessage: body => {
        if (!active) return
        const event = parseChatEvent(body)
        if (!event) return
        if (event.type === 'RELATIONSHIP_CHANGED') { relationshipsChanged(); return }
        const version = (versions.get(event.conversationId) || 0) + 1
        versions.set(event.conversationId, version)
        if (event.type === 'MESSAGE_CREATED' && !event.message.deletedAt && event.message.senderId !== userId) {
          setNewConversations(previous => new Set([...previous, event.conversationId]))
        } else if (event.type === 'MESSAGES_READ' && event.read.readerId === userId) {
          acknowledgeRead(event.conversationId, event.read.unreadCount)
        } else if (event.type === 'MESSAGE_RECALLED') {
          void chatApi.get(event.conversationId).then(response => {
            if (active && versions.get(event.conversationId) === version) acknowledgeRead(event.conversationId, response.data.unreadCount)
          }).catch(() => {})
        }
        for (const listener of listeners.current) listener(event)
        refresh()
      },
    })
    socket.current = connection
    const online = () => connection.reconnect()
    const visible = () => { if (document.visibilityState === 'visible') connection.reconnect() }
    window.addEventListener('online', online)
    document.addEventListener('visibilitychange', visible)
    return () => { active = false; socket.current = null; connection.stop(); window.removeEventListener('online', online); document.removeEventListener('visibilitychange', visible) }
  }, [userId, refresh, acknowledgeRead, relationshipsChanged])
  const value = useMemo(() => ({ relationshipRevision, relationshipsChanged, status, revision, connectionRevision, subscribe, subscribeTyping, sendTyping, refresh, acknowledgeRead, hasNewMessages: newConversations.size > 0 }), [relationshipRevision, relationshipsChanged, status, revision, connectionRevision, subscribe, subscribeTyping, sendTyping, refresh, acknowledgeRead, newConversations])
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat() {
  const value = useContext(ChatContext)
  if (!value) throw new Error('ChatProvider is required')
  return value
}
