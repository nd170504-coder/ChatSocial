import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { env } from '../../config/env'
import { authenticatedRequest, authStore } from '../auth/session'
import { connectUserChannel } from '../notifications/socket'
import { notificationSocketUrl } from '../notifications/utils'
import { parsePresence } from './state'
import './presence.css'

const PresenceContext = createContext<ReadonlySet<string>>(new Set())

export function PresenceProvider({ userId, children }: { userId: string; children: ReactNode }) {
  const [online, setOnline] = useState<ReadonlySet<string>>(new Set())
  useEffect(() => {
    let active = true, generation = 0, version = 0
    let controller: AbortController | undefined
    let expiry: ReturnType<typeof setTimeout> | undefined
    const clear = () => { clearTimeout(expiry); setOnline(new Set()) }
    const accept = (value: unknown) => {
      const snapshot = parsePresence(value)
      if (!active || !snapshot) return
      version++
      setOnline(new Set(snapshot.onlineUserIds))
      clearTimeout(expiry)
      // Hide stale dots if updates stop without a clean socket close.
      expiry = setTimeout(clear, 20_000)
    }
    const connection = connectUserChannel({
      url: notificationSocketUrl(env.apiBaseUrl, window.location.href),
      destination: '/user/queue/presence',
      getToken: async () => {
        const token = await authStore.getAccessToken()
        if (!active || authStore.getSnapshot().session?.user.id !== userId) throw new Error('Session changed')
        return token
      },
      onStatus: status => {
        if (active && status !== 'connected') { generation++; controller?.abort(); clear() }
      },
      onReady: () => {
        const current = ++generation, startedVersion = version
        controller?.abort(); controller = new AbortController()
        void authenticatedRequest<unknown>('/presence/friends', { signal: controller.signal })
          .then(response => {
            // An older HTTP snapshot must not replace a newer socket update.
            if (active && current === generation && startedVersion === version) accept(response.data)
          }).catch(() => { /* The next periodic snapshot will retry automatically. */ })
      },
      onMessage: body => { try { accept(JSON.parse(body)) } catch { /* Ignore malformed frames. */ } },
    })
    const onlineAgain = () => connection.reconnect()
    const visibility = () => { if (document.visibilityState === 'visible') connection.reconnect() }
    window.addEventListener('online', onlineAgain)
    document.addEventListener('visibilitychange', visibility)
    return () => {
      active = false; controller?.abort(); clearTimeout(expiry); connection.stop()
      window.removeEventListener('online', onlineAgain)
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [userId])
  return <PresenceContext.Provider value={online}>{children}</PresenceContext.Provider>
}

export function useOnline(userId?: string) {
  const online = useContext(PresenceContext)
  return !!userId && online.has(userId)
}
