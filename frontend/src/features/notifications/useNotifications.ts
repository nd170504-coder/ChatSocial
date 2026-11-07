import { useCallback, useEffect, useState } from 'react'
import { env } from '../../config/env'
import { authStore } from '../auth/session'
import { notificationsApi } from './api'
import { connectNotifications, type ConnectionStatus } from './socket'
import type { NotificationItem, NotificationPage } from './types'
import { notificationSocketUrl } from './utils'

export function useNotifications(userId: string, open: boolean) {
  const [unread, setUnread] = useState<number | null>(null)
  const [countError, setCountError] = useState(false)
  const [data, setData] = useState<NotificationPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState(false)
  const [request, setRequest] = useState({ page: 0, attempt: 0 })
  const [revision, setRevision] = useState(0)
  const [status, setStatus] = useState<ConnectionStatus>('connecting')
  const [latest, setLatest] = useState<NotificationItem | null>(null)

  const refresh = useCallback(() => {
    setRevision(value => value + 1)
    setRequest(current => ({ page: 0, attempt: current.attempt + 1 }))
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void notificationsApi.unread(controller.signal).then(response => {
      if (active) { setUnread(response.data.unreadCount); setCountError(false) }
    }).catch(() => { if (active) setCountError(true) })
    return () => { active = false; controller.abort() }
  }, [userId, revision])

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    let active = true
    setLoading(true); setListError(false)
    void notificationsApi.list(request.page, controller.signal).then(response => {
      if (!active) return
      setData(previous => {
        if (!previous || request.page === 0) return response.data
        const ids = new Set(previous.items.map(item => item.id))
        return { ...response.data, items: [...previous.items, ...response.data.items.filter(item => !ids.has(item.id))] }
      })
    }).catch(() => { if (active) setListError(true) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [userId, open, request])

  useEffect(() => {
    let active = true
    let queued: ReturnType<typeof setTimeout> | undefined
    const connection = connectNotifications({
      url: notificationSocketUrl(env.apiBaseUrl, window.location.href),
      getToken: async () => {
        const token = await authStore.getAccessToken()
        if (!active || authStore.getSnapshot().session?.user.id !== userId) throw new Error('Session changed')
        return token
      },
      onNotification: item => {
        if (!active) return
        setLatest(item)
        clearTimeout(queued)
        // REST remains authoritative for unread counts and read states, including duplicate frames.
        queued = setTimeout(() => { if (active) refresh() }, 100)
      },
      onReady: () => { if (active) refresh() },
      onStatus: value => { if (active) setStatus(value) },
    })
    const online = () => connection.reconnect()
    const visible = () => { if (document.visibilityState === 'visible') { refresh(); connection.reconnect() } }
    window.addEventListener('online', online)
    document.addEventListener('visibilitychange', visible)
    return () => {
      active = false; clearTimeout(queued); connection.stop()
      window.removeEventListener('online', online)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [userId, refresh])

  return {
    unread, countError, data, loading, listError, status, latest, refresh,
    loadMore: () => { if (!loading && data?.hasNext) setRequest(current => ({ page: data.page + 1, attempt: current.attempt + 1 })) },
    retryList: () => setRequest(current => ({ ...current, attempt: current.attempt + 1 })),
  }
}
