import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { notificationSocketUrl, notificationTarget, parseNotification } from '../src/features/notifications/utils'
import { tokenExpiresAt } from '../src/features/auth/token'
import type { NotificationItem } from '../src/features/notifications/types'

const mocks = vi.hoisted(() => ({ clients: [] as Array<{
  connectHeaders: Record<string, string>; activate: ReturnType<typeof vi.fn>; deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>; forceDisconnect: ReturnType<typeof vi.fn>
  onConnect: () => void; onWebSocketClose: () => void; onStompError: () => void
}> }))
vi.mock('@stomp/stompjs', () => ({ Client: vi.fn(function(config) {
  const client = { ...config, activate: vi.fn(), deactivate: vi.fn().mockResolvedValue(undefined), subscribe: vi.fn(), forceDisconnect: vi.fn() }
  mocks.clients.push(client)
  return client
}) }))

const notification: NotificationItem = { id: 'n-1', type: 'POST_LIKED', actorId: 'actor', actorName: 'Minh', actorAvatarUrl: null, postId: 'post/1', createdAt: '2026-11-08T00:00:00Z', readAt: null }
const token = (exp: number) => `header.${btoa(JSON.stringify({ exp }))}.signature`
const flush = async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve() }
beforeEach(() => { mocks.clients.length = 0 })
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

it('derives the root WebSocket URL without including API paths, queries or credentials', () => {
  expect(notificationSocketUrl('http://localhost:8080/api', 'http://localhost:5173/feed')).toBe('ws://localhost:8080/ws')
  expect(notificationSocketUrl('/api', 'https://example.com/feed')).toBe('wss://example.com/ws')
  expect(notificationSocketUrl('https://example.com/social/api/?x=1', 'https://example.com')).toBe('wss://example.com/social/ws')
  expect(notificationSocketUrl('https://user:password@example.com/api', 'https://example.com')).toBe('wss://example.com/ws')
})

it('validates incoming frames and builds internal notification links', () => {
  expect(parseNotification(JSON.stringify(notification))).toEqual(notification)
  expect(notificationTarget(notification)).toBe('/posts/post%2F1')
  expect(notificationTarget({ ...notification, type: 'FRIEND_REQUEST', postId: null })).toBe('/users/actor')
  for (const body of ['bad json', '{}', JSON.stringify({ ...notification, type: 'UNKNOWN' }), JSON.stringify({ ...notification, createdAt: 'bad' })]) expect(parseNotification(body)).toBeNull()
  expect(tokenExpiresAt(token(2_000_000_000))).toBe(2_000_000_000_000)
  expect(tokenExpiresAt('bad-token')).toBeNull()
})

it('uses all REST notification contracts with authentication', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, data }))
  const { authStore } = await import('../src/features/auth/session')
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  await authStore.login({ email: 'viewer@example.com', password: 'password123' })
  const { notificationsApi } = await import('../src/features/notifications/api')
  fetchMock.mockResolvedValueOnce(response({ items: [notification], page: 2, size: 20, hasNext: false }))
    .mockResolvedValueOnce(response({ unreadCount: 1 }))
    .mockResolvedValueOnce(response({ ...notification, readAt: notification.createdAt }))
    .mockResolvedValueOnce(response({ unreadCount: 0 }))
  const signal = new AbortController().signal
  expect((await notificationsApi.list(2, signal)).data.items).toEqual([notification])
  expect((await notificationsApi.unread(signal)).data.unreadCount).toBe(1)
  await notificationsApi.read('n/1')
  await notificationsApi.readAll()
  expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/notifications\?page=2&size=20$/)
  expect(fetchMock.mock.calls[1][1]?.signal).toBe(signal)
  expect(String(fetchMock.mock.calls[3][0])).toMatch(/\/notifications\/n%2F1\/read$/)
  expect(String(fetchMock.mock.calls[4][0])).toMatch(/\/notifications\/read-all$/)
  for (const [, options] of fetchMock.mock.calls.slice(3)) expect(options?.method).toBe('PATCH')
  for (const [, options] of fetchMock.mock.calls.slice(1)) expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
})

it('subscribes after connecting and stops reconnecting after logout/unmount', async () => {
  vi.useFakeTimers()
  const { connectNotifications } = await import('../src/features/notifications/socket')
  const onNotification = vi.fn(), onReady = vi.fn(), onStatus = vi.fn()
  const connection = connectNotifications({ url: 'ws://localhost/ws', getToken: async () => token(Date.now() / 1000 + 3600), onNotification, onReady, onStatus })
  await flush()
  const client = mocks.clients[0]
  expect(client.connectHeaders.Authorization).toMatch(/^Bearer /)
  client.onConnect()
  expect(client.subscribe.mock.calls[0][0]).toBe('/user/queue/notifications')
  expect(onReady).toHaveBeenCalledOnce()
  client.subscribe.mock.calls[0][1]({ body: JSON.stringify(notification) })
  client.subscribe.mock.calls[0][1]({ body: '{}' })
  expect(onNotification).toHaveBeenCalledExactlyOnceWith(notification)
  connection.stop()
  client.onWebSocketClose()
  await vi.advanceTimersByTimeAsync(10_000)
  expect(mocks.clients).toHaveLength(1)
  expect(client.deactivate).toHaveBeenCalledWith({ force: true })
})

it('shares token rotation between simultaneous socket token requests', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, data }))
  const { authStore } = await import('../src/features/auth/session')
  const session = { accessToken: token(Date.now() / 1000 - 5), refreshToken: 'refresh', user: { id: 'viewer' } }
  const fresh = token(Date.now() / 1000 + 3600)
  fetchMock.mockResolvedValueOnce(response(session)).mockResolvedValueOnce(response({ ...session, accessToken: fresh }))
  await authStore.login({ email: 'viewer@example.com', password: 'password123' })
  expect(await Promise.all([authStore.getAccessToken(), authStore.getAccessToken()])).toEqual([fresh, fresh])
  expect(fetchMock).toHaveBeenCalledTimes(2)
  expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/auth\/refresh$/)
  expect(await authStore.getAccessToken()).toBe(fresh)
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('reconnects using a fresh token before expiry and retries temporary token failures', async () => {
  vi.useFakeTimers()
  const { connectNotifications } = await import('../src/features/notifications/socket')
  const getToken = vi.fn().mockRejectedValueOnce(new TypeError('offline'))
    .mockImplementation(async () => token(Date.now() / 1000 + 60))
  const connection = connectNotifications({ url: 'ws://localhost/ws', getToken, onNotification: vi.fn(), onReady: vi.fn(), onStatus: vi.fn() })
  await flush()
  expect(mocks.clients).toHaveLength(0)
  await vi.advanceTimersByTimeAsync(5000)
  expect(mocks.clients).toHaveLength(1)
  mocks.clients[0].onConnect()
  await vi.advanceTimersByTimeAsync(45_000)
  expect(getToken).toHaveBeenCalledTimes(3)
  expect(mocks.clients).toHaveLength(2)
  connection.stop()
})

it('does not open a socket when a token arrives after teardown', async () => {
  const { connectNotifications } = await import('../src/features/notifications/socket')
  let resolve!: (value: string) => void
  const pending = new Promise<string>(done => { resolve = done })
  const connection = connectNotifications({ url: 'ws://localhost/ws', getToken: () => pending, onNotification: vi.fn(), onReady: vi.fn(), onStatus: vi.fn() })
  connection.stop()
  resolve(token(2_000_000_000))
  await flush()
  expect(mocks.clients).toHaveLength(0)
})
