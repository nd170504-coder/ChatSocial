import { afterEach, expect, it, vi } from 'vitest'
import { applyRead, compareMessages, mergeMessages, parseChatEvent } from '../src/features/chat/utils'
import type { ChatMessage, ChatRead } from '../src/features/chat/types'

afterEach(() => vi.unstubAllGlobals())
const message = (id: string, createdAt: string, extra: Partial<ChatMessage> = {}): ChatMessage => ({ id, conversationId: 'chat-1', senderId: 'sender', content: 'Hello', media: [], clientMessageId: 'client-1', createdAt, readByRecipient: false, ...extra })

it('merges REST and WebSocket messages once, preserves read state and sorts sub-millisecond timestamps', () => {
  const first = message('z', '2026-11-09T00:00:00Z', { readByRecipient: true })
  const second = message('a', '2026-11-09T00:00:00.000100Z')
  const third = message('b', '2026-11-09T00:00:00.000200Z')
  const merged = mergeMessages([third, first], [second, { ...first, readByRecipient: false }])
  expect(merged.map(item => item.id)).toEqual(['z', 'a', 'b'])
  expect(merged[0].readByRecipient).toBe(true)
  expect(compareMessages(first, second)).toBeLessThan(0)
  expect(mergeMessages([], [{ ...third, readByRecipient: true }, first, second]).every(item => item.readByRecipient)).toBe(true)
})

it('applies a read receipt only to messages through its timestamp/id boundary in the correct chat', () => {
  const items = [message('a', '2026-11-09T00:00:00Z'), message('b', '2026-11-09T00:00:01Z'), message('c', '2026-11-09T00:00:02Z'), message('other', '2026-11-09T00:00:00Z', { conversationId: 'other' })]
  const read: ChatRead = { conversationId: 'chat-1', readerId: 'recipient', lastReadMessageId: 'b', lastReadMessageCreatedAt: items[1].createdAt, unreadCount: 1 }
  expect(applyRead(items, read).map(item => item.readByRecipient)).toEqual([true, true, false, false])
  expect(applyRead(applyRead(items, read), { ...read, lastReadMessageId: 'a', lastReadMessageCreatedAt: items[0].createdAt })[1].readByRecipient).toBe(true)
})

it('rejects malformed events and accepts chat messages and read receipts', () => {
  const item = message('one', '2026-11-09T00:00:00Z')
  const event = { type: 'MESSAGE_CREATED', conversationId: 'chat-1', message: item, read: null }
  expect(parseChatEvent(JSON.stringify(event))).toEqual(event)
  expect(parseChatEvent(JSON.stringify({ ...event, conversationId: 'wrong' }))).toBeNull()
  expect(parseChatEvent(JSON.stringify({ ...event, message: { ...item, createdAt: 'bad' } }))).toBeNull()
  expect(parseChatEvent('bad-json')).toBeNull()
  expect(parseChatEvent(JSON.stringify({ type: 'MESSAGES_READ', conversationId: 'chat-1', message: null, read: { conversationId: 'chat-1', readerId: 'reader', lastReadMessageId: 'one', lastReadMessageCreatedAt: item.createdAt, unreadCount: 0 } }))?.type).toBe('MESSAGES_READ')
})

it('accepts image-only socket messages and rejects empty or malformed media', () => {
  const item = message('photo', '2026-11-12T00:00:00Z', { content: null, media: [{ id: 'media-1', url: '/api/conversations/chat-1/messages/photo/media/media-1', mediaType: 'IMAGE' }] })
  const event = { type: 'MESSAGE_CREATED', conversationId: 'chat-1', message: item, read: null }
  expect(parseChatEvent(JSON.stringify(event))).toEqual(event)
  expect(parseChatEvent(JSON.stringify({ ...event, message: { ...item, media: [] } }))).toBeNull()
  expect(parseChatEvent(JSON.stringify({ ...event, message: { ...item, media: [{}] } }))).toBeNull()
  expect(mergeMessages([item], [item])).toHaveLength(1)
  expect(mergeMessages([item], [item])[0].media).toEqual(item.media)
})

it('sends image form-data with a stable retry id and loads private images with JWT', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } } })))
  const { authStore } = await import('../src/features/auth/session')
  await authStore.login({ email: 'viewer@example.com', password: 'Password123!' })
  const { chatApi } = await import('../src/features/chat/api')
  fetchMock.mockImplementation(async () => new Response(JSON.stringify({ success: true, data: null })))
  const file = new File(['image bytes'], 'photo.png', { type: 'image/png' })
  await chatApi.sendImage('chat/id', file, 'retry-id')
  await chatApi.sendImage('chat/id', file, 'retry-id')
  for (const [url, options] of fetchMock.mock.calls.slice(1)) {
    expect(String(url)).toMatch(/\/conversations\/chat%2Fid\/images$/)
    expect(options?.method).toBe('POST')
    const body = options?.body as FormData
    expect(body.get('clientMessageId')).toBe('retry-id')
    expect((body.get('file') as File).name).toBe('photo.png')
    expect(new Headers(options?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
  }
  fetchMock.mockResolvedValueOnce(new Response(new Blob(['private'], { type: 'image/png' }), { headers: { 'Content-Type': 'image/png' } }))
  const signal = new AbortController().signal
  expect((await chatApi.image('chat/id', 'message/id', 'media/id', signal)).type).toBe('image/png')
  const [url, options] = fetchMock.mock.calls.at(-1)!
  expect(String(url)).toMatch(/\/conversations\/chat%2Fid\/messages\/message%2Fid\/media\/media%2Fid$/)
  expect(options?.signal).toBe(signal)
  expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
})

it('uses all six chat endpoints, explicit send ids, cursors and JWT authentication', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, data }))
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  const { authStore } = await import('../src/features/auth/session')
  await authStore.login({ email: 'viewer@example.com', password: 'Password123!' })
  const { chatApi } = await import('../src/features/chat/api')
  fetchMock.mockImplementation(async () => response(null))
  const signal = new AbortController().signal
  await chatApi.open('other')
  await chatApi.list(2, signal)
  await chatApi.get('chat/id', signal)
  await chatApi.history('chat/id', 'before/id', signal)
  await chatApi.send('chat/id', 'Hello', 'same-client-id')
  await chatApi.read('chat/id', 'message-id')
  const calls = fetchMock.mock.calls
  expect(String(calls[1][0])).toMatch(/\/conversations\/direct$/)
  expect(JSON.parse(calls[1][1]?.body as string)).toEqual({ userId: 'other' })
  expect(String(calls[2][0])).toMatch(/\/conversations\?page=2&size=20$/)
  expect(String(calls[3][0])).toMatch(/\/conversations\/chat%2Fid$/)
  expect(String(calls[4][0])).toMatch(/\/messages\?size=20&before=before%2Fid$/)
  expect(calls[4][1]?.signal).toBe(signal)
  expect(calls[5][1]?.method).toBe('POST')
  expect(JSON.parse(calls[5][1]?.body as string)).toEqual({ content: 'Hello', clientMessageId: 'same-client-id' })
  expect(calls[6][1]?.method).toBe('PUT')
  expect(JSON.parse(calls[6][1]?.body as string)).toEqual({ lastReadMessageId: 'message-id' })
  for (const [, options] of calls.slice(1)) expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
})
