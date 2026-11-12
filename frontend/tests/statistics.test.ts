import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => vi.unstubAllGlobals())

it('loads personal statistics with authentication and cancellation', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  const user = { id: 'me', fullName: 'Minh', username: 'minh', email: 'preview@example.com', status: 'ACTIVE' }
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user }))
  const { authStore } = await import('../src/features/auth/session')
  await authStore.login({ email: user.email, password: 'example-password' })
  const totals = { postCount: 3, friendCount: 5, receivedLikeCount: 12, receivedCommentCount: 8 }
  fetchMock.mockResolvedValueOnce(response(totals))
  const { statisticsApi } = await import('../src/features/statistics/api')
  const signal = new AbortController().signal
  expect((await statisticsApi.get(signal)).data).toEqual(totals)
  const [url, options] = fetchMock.mock.calls[1]
  expect(String(url)).toMatch(/\/api\/users\/me\/statistics$/)
  expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
  expect(options?.signal).toBe(signal)
  expect(options?.body).toBeUndefined()
})
