import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { env } from '../../config/env'
import { authStore } from '../auth/session'
import { connectUserChannel, type ConnectionStatus } from '../notifications/socket'
import { notificationSocketUrl } from '../notifications/utils'
import type { ChatEvent } from './types'
import { parseChatEvent } from './utils'

const ChatContext = createContext<{
  status: ConnectionStatus; revision: number; connectionRevision: number
  subscribe: (listener: (event: ChatEvent) => void) => () => void
  refresh: () => void
  hasNewMessages: boolean
  acknowledgeRead: (id: string, unread: number) => void
} | null>(null)

export function ChatProvider({ userId, children }: { userId: string; children: ReactNode }) {
  const [status, setStatus] = useState<ConnectionStatus>('connecting')
  const [revision, setRevision] = useState(0)
  const [connectionRevision, setConnectionRevision] = useState(0)
  const [newConversations, setNewConversations] = useState(new Set<string>())
  const listeners = useRef(new Set<(event: ChatEvent) => void>())
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
    const connection = connectUserChannel({
      url: notificationSocketUrl(env.apiBaseUrl, window.location.href), destination: '/user/queue/chat',
      getToken: async () => {
        const token = await authStore.getAccessToken()
        if (!active || authStore.getSnapshot().session?.user.id !== userId) throw new Error('Session changed')
        return token
      },
      onReady: () => { if (active) { refresh(); setConnectionRevision(value => value + 1) } },
      onStatus: value => { if (active) setStatus(value) },
      onMessage: body => {
        if (!active) return
        const event = parseChatEvent(body)
        if (!event) return
        if (event.type === 'MESSAGE_CREATED' && event.message.senderId !== userId) {
          setNewConversations(previous => new Set([...previous, event.conversationId]))
        } else if (event.type === 'MESSAGES_READ' && event.read.readerId === userId) {
          acknowledgeRead(event.conversationId, event.read.unreadCount)
        }
        for (const listener of listeners.current) listener(event)
        refresh()
      },
    })
    const online = () => connection.reconnect()
    const visible = () => { if (document.visibilityState === 'visible') connection.reconnect() }
    window.addEventListener('online', online)
    document.addEventListener('visibilitychange', visible)
    return () => { active = false; connection.stop(); window.removeEventListener('online', online); document.removeEventListener('visibilitychange', visible) }
  }, [userId, refresh, acknowledgeRead])
  const value = useMemo(() => ({ status, revision, connectionRevision, subscribe, refresh, acknowledgeRead, hasNewMessages: newConversations.size > 0 }), [status, revision, connectionRevision, subscribe, refresh, acknowledgeRead, newConversations])
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat() {
  const value = useContext(ChatContext)
  if (!value) throw new Error('ChatProvider is required')
  return value
}
