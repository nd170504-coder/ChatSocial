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
  let stopped = false
  let generation = 0
  let client: Client | null = null
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
        heartbeatIncoming: 0,
        heartbeatOutgoing: 10_000,
        debug: () => {},
      })
      client = current
      const isCurrent = () => !stopped && client === current && generation === started
      current.onConnect = () => {
        if (!isCurrent()) return
        current.subscribe('/user/queue/notifications', message => {
          if (!isCurrent()) return
          const item = parseNotification(message.body)
          if (item) options.onNotification(item)
        })
        options.onStatus('connected')
        options.onReady()
        const expiresAt = tokenExpiresAt(token)
        if (expiresAt !== null) {
          const remaining = expiresAt - Date.now()
          const margin = Math.min(15_000, Math.max(1000, remaining / 4))
          renewal = setTimeout(() => { if (isCurrent()) void connect() }, Math.max(1000, remaining - margin))
        }
      }
      current.onWebSocketClose = () => { if (isCurrent()) { clearTimeout(renewal); schedule() } }
      current.onStompError = () => { if (isCurrent()) { current.forceDisconnect(); schedule() } }
      current.onWebSocketError = () => { if (isCurrent()) options.onStatus('reconnecting') }
      current.activate()
    } catch {
      if (!stopped && generation === started) schedule()
    }
  }

  void connect()
  return {
    reconnect: () => { void connect() },
    stop: () => {
      stopped = true; generation++
      clearTimeout(retry); clearTimeout(renewal)
      const previous = client; client = null
      if (previous) void previous.deactivate({ force: true }).catch(() => {})
    },
  }
}
