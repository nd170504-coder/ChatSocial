import { Client } from '@stomp/stompjs'
import { tokenExpiresAt } from '../auth/token'
import { parseNotification } from './utils'
import type { NotificationItem } from './types'

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting'

export function connectNotifications(options: {
  url: string
  getToken: () => Promise<string>
  onNotification: (item: NotificationItem) => void
  onReady: () => void
  onStatus: (status: ConnectionStatus) => void
}) {
  return connectUserChannel({ ...options, destination: '/user/queue/notifications', onMessage: body => {
    const item = parseNotification(body)
    if (item) options.onNotification(item)
  } })
}

export function connectUserChannel(options: {
  url: string
  destination: '/user/queue/notifications' | '/user/queue/chat' | '/user/queue/presence'
  getToken: () => Promise<string>
  onMessage: (body: string) => void
  onTyping?: (body: string) => void
  onReady: () => void
  onStatus: (status: ConnectionStatus) => void
}) {
  let stopped = false
  let generation = 0
  let client: Client | null = null
  let ready = false
  let retry: ReturnType<typeof setTimeout> | undefined
  let renewal: ReturnType<typeof setTimeout> | undefined

  function schedule() {
    if (stopped || retry) return
    options.onStatus('reconnecting')
    retry = setTimeout(() => { retry = undefined; void connect() }, 5000)
  }

  async function connect() {
    if (stopped) return
    const started = ++generation
    ready = false
    clearTimeout(retry); retry = undefined
    clearTimeout(renewal)
    const previous = client
    client = null
    if (previous) await previous.deactivate({ force: true }).catch(() => {})
    if (stopped || generation !== started) return
    options.onStatus('connecting')
    try {
      const token = await options.getToken()
      if (stopped || generation !== started) return
      const current = new Client({
        brokerURL: options.url,
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 0,
        connectionTimeout: 10_000,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        debug: () => {},
      })
      client = current
      const isCurrent = () => !stopped && client === current && generation === started
      current.onConnect = () => {
        if (!isCurrent()) return
        current.subscribe(options.destination, message => {
          if (!isCurrent()) return
          options.onMessage(message.body)
        })
        if (options.onTyping) current.subscribe('/user/queue/typing', message => {
          if (isCurrent()) options.onTyping?.(message.body)
        })
        ready = true
        options.onStatus('connected')
        options.onReady()
        const expiresAt = tokenExpiresAt(token)
        if (expiresAt !== null) {
          const remaining = expiresAt - Date.now()
          const margin = Math.min(15_000, Math.max(1000, remaining / 4))
          renewal = setTimeout(() => { if (isCurrent()) void connect() }, Math.max(1000, remaining - margin))
        }
      }
      current.onWebSocketClose = () => { if (isCurrent()) { ready = false; clearTimeout(renewal); schedule() } }
      current.onStompError = () => { if (isCurrent()) { ready = false; current.forceDisconnect(); schedule() } }
      current.onWebSocketError = () => { if (isCurrent()) { ready = false; options.onStatus('reconnecting') } }
      current.activate()
    } catch {
      if (!stopped && generation === started) schedule()
    }
  }

  void connect()
  return {
    sendTyping: (conversationId: string, typing: boolean) => {
      // Typing is temporary: never queue or replay it after reconnecting.
      if (stopped || !ready || !client || !options.onTyping) return false
      try {
        client.publish({ destination: '/app/chat/typing', body: JSON.stringify({ conversationId, typing }) })
        return true
      } catch { return false }
    },
    reconnect: () => { void connect() },
    stop: () => {
      stopped = true; ready = false; generation++
      clearTimeout(retry); clearTimeout(renewal)
      const previous = client; client = null
      if (previous) void previous.deactivate({ force: true }).catch(() => {})
    },
  }
}
