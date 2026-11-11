import { afterEach, expect, it, vi } from 'vitest'
import { parseChatEvent } from '../src/features/chat/utils'

afterEach(() => vi.unstubAllGlobals())

it('recognizes a relationship refresh without treating it as a message or read receipt', () => {
  const event = { type: 'RELATIONSHIP_CHANGED', conversationId: 'conversation', message: null, read: null }
  expect(parseChatEvent(JSON.stringify(event))).toEqual(event)
  expect(parseChatEvent(JSON.stringify({ ...event, message: {} }))).toBeNull()
})

it('uses authenticated block, unblock, own-list and status contracts', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, data }))
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  const { authStore } = await import('../src/features/auth/session')
  await authStore.login({ email: 'viewer@example.com', password: 'Password123!' })
  fetchMock.mockImplementation(async () => response(null))
  const { blocksApi } = await import('../src/features/blocks/api')
  const signal = new AbortController().signal
  await blocksApi.status('user/id', signal)
  await blocksApi.list(2, signal)
  await blocksApi.change('user/id', true)
  await blocksApi.change('user/id', false)
  const calls = fetchMock.mock.calls.slice(1)
  expect(String(calls[0][0])).toMatch(/\/blocks\/user%2Fid$/)
  expect(String(calls[1][0])).toMatch(/\/blocks\?page=2&size=20$/)
  expect(calls[0][1]?.signal).toBe(signal)
  expect(calls[2][1]?.method).toBe('PUT')
  expect(calls[3][1]?.method).toBe('DELETE')
  for (const [, options] of calls) expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
})
