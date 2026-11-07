import { afterEach, expect, it, vi } from 'vitest'
import { normalizeUserQuery } from '../src/features/friends/utils'

afterEach(() => vi.unstubAllGlobals())

it('normalizes username/email and rejects unusable search terms', () => {
  expect(normalizeUserQuery(' @Minh_Anh ')).toBe('minh_anh')
  expect(normalizeUserQuery(' Alice@Example.com ')).toBe('alice@example.com')
  for (const value of ['', ' ', '@a', 'a'.repeat(256)]) expect(() => normalizeUserQuery(value)).toThrow('2 đến 255')
})

it('encodes email plus signs and pagination and sends authentication', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const { authStore } = await import('../src/features/auth/session')
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  await authStore.login({ email: 'viewer@example.com', password: 'test-password' })
  fetchMock.mockResolvedValueOnce(response({ items: [], page: 2, size: 12, hasNext: false }))
  const { friendSearchApi } = await import('../src/features/friends/api')
  const controller = new AbortController()
  await friendSearchApi.search('alice+test@example.com', 2, controller.signal)
  const [path, options] = fetchMock.mock.calls[1]
  const url = new URL(String(path), 'http://localhost')
  expect(url.pathname).toBe('/api/users/search')
  expect(url.searchParams.get('q')).toBe('alice+test@example.com')
  expect(url.searchParams.get('page')).toBe('2')
  expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
  expect(options?.signal).toBe(controller.signal)
})

it('uses the implemented friends list and per-user count contracts with authentication', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  const { authStore } = await import('../src/features/auth/session')
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  await authStore.login({ email: 'viewer@example.com', password: 'password123' })
  const { friendsApi } = await import('../src/features/friends/api')
  const controller = new AbortController()
  const friend = { userId: 'friend-id', username: 'minh', fullName: 'Minh Anh', avatarUrl: null }
  fetchMock.mockResolvedValueOnce(response({ items: [friend], page: 2, size: 12, hasNext: false }))
  expect((await friendsApi.list(2, controller.signal)).data.items[0].userId).toBe('friend-id')
  const [listUrl, listOptions] = fetchMock.mock.calls[1]
  expect(String(listUrl)).toMatch(/\/api\/friends\?page=2&size=12$/)
  expect(new Headers(listOptions?.headers).get('Authorization')).toBe('Bearer access')
  expect(listOptions?.signal).toBe(controller.signal)
  fetchMock.mockResolvedValueOnce(response({ userId: 'friend-id', friendCount: 0 }))
  expect((await friendsApi.count('friend-id', controller.signal)).data.friendCount).toBe(0)
  const [countUrl, countOptions] = fetchMock.mock.calls[2]
  expect(String(countUrl)).toMatch(/\/api\/users\/friend-id\/friends\/count$/)
  expect(new Headers(countOptions?.headers).get('Authorization')).toBe('Bearer access')
  expect(countOptions?.signal).toBe(controller.signal)
  fetchMock.mockResolvedValueOnce(response(null))
  await friendsApi.unfriend('friend/id')
  const [deleteUrl, deleteOptions] = fetchMock.mock.calls[3]
  expect(String(deleteUrl)).toMatch(/\/api\/friends\/friend%2Fid$/)
  expect(deleteOptions?.method).toBe('DELETE')
  expect(deleteOptions?.body).toBeUndefined()
  expect(new Headers(deleteOptions?.headers).get('Authorization')).toBe('Bearer access')
})
