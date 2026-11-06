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
