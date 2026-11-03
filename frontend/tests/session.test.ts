import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../src/features/auth/types'

const user = { id: 'user-1', email: 'test@example.com', username: 'tester', fullName: 'Test User', status: 'ACTIVE' as const }
function tokens(suffix = 'old'): AuthSession {
  return { accessToken: `access-${suffix}`, refreshToken: `refresh-${suffix}`, tokenType: 'Bearer', expiresIn: 60, refreshExpiresIn: 3600, sessionId: 'session-1', user }
}
function ok(data: unknown) { return new Response(JSON.stringify({ success: true, message: 'OK', data }), { status: 200 }) }
function unauthorized() { return new Response(JSON.stringify({ title: 'Unauthorized', detail: 'Invalid session' }), { status: 401 }) }
const fetchMock = vi.fn<typeof fetch>()
let storage: Map<string, string>

beforeEach(() => {
  vi.resetModules()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  storage = new Map()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => { storage.set(key, value) },
    removeItem: (key: string) => { storage.delete(key) },
  })
})

describe('Auth session lifecycle', () => {
  it('starts anonymous without making a request when storage is empty', async () => {
    const { authStore } = await import('../src/features/auth/session')
    await authStore.initialize()
    expect(authStore.getSnapshot().status).toBe('anonymous')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not refresh failed login or store the password', async () => {
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(unauthorized())
    await expect(authStore.login({ email: user.email, password: 'wrong-password' })).rejects.toThrow()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(storage.size).toBe(0)
  })

  it('coalesces concurrent refreshes and persists both rotated tokens', async () => {
    const { authStore, authenticatedRequest } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(ok(tokens()))
    await authStore.login({ email: user.email, password: 'password123' })
    fetchMock.mockImplementation(async (url, options) => {
      if (String(url).endsWith('/auth/refresh')) {
        expect(JSON.parse(String(options?.body))).toEqual({ refreshToken: 'refresh-old' })
        return ok(tokens('new'))
      }
      const token = new Headers(options?.headers).get('Authorization')
      return token === 'Bearer access-new' ? ok(user) : unauthorized()
    })
    const responses = await Promise.all([
      authenticatedRequest('/users/me'), authenticatedRequest('/users/me'), authenticatedRequest('/users/me'),
    ])
    expect(responses.every(response => response.success)).toBe(true)
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1)
    expect(JSON.parse(storage.get('chatsocial.auth')!).refreshToken).toBe('refresh-new')
    expect(authStore.getSnapshot().session?.accessToken).toBe('access-new')
  })

  it('deduplicates restoration and verifies the stored user with the backend', async () => {
    storage.set('chatsocial.auth', JSON.stringify(tokens()))
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(ok({ ...user, fullName: 'Updated Name' }))
    await Promise.all([authStore.initialize(), authStore.initialize()])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(authStore.getSnapshot().session?.user.fullName).toBe('Updated Name')
    expect(authStore.getSnapshot().status).toBe('authenticated')
  })

  it('clears expired sessions when refresh is rejected', async () => {
    storage.set('chatsocial.auth', JSON.stringify(tokens()))
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockImplementation(async () => unauthorized())
    await authStore.initialize()
    expect(authStore.getSnapshot()).toEqual({ status: 'anonymous', session: null })
    expect(storage.size).toBe(0)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('keeps tokens on a network failure and allows retrying restoration', async () => {
    storage.set('chatsocial.auth', JSON.stringify(tokens()))
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await authStore.initialize()
    expect(authStore.getSnapshot().status).toBe('error')
    expect(storage.size).toBe(1)
    fetchMock.mockResolvedValueOnce(ok(user))
    await authStore.initialize()
    expect(authStore.getSnapshot().status).toBe('authenticated')
  })

  it('revokes logout on the backend before clearing local state', async () => {
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(ok(tokens()))
    await authStore.login({ email: user.email, password: 'password123' })
    fetchMock.mockRejectedValueOnce(new TypeError('Offline'))
    await expect(authStore.logout()).rejects.toThrow()
    expect(authStore.getSnapshot().status).toBe('authenticated')
    fetchMock.mockResolvedValueOnce(ok(null))
    await authStore.logout()
    expect(fetchMock.mock.lastCall?.[0]).toMatch(/\/auth\/logout$/)
    expect(fetchMock.mock.lastCall?.[1]?.method).toBe('POST')
    expect(authStore.getSnapshot().status).toBe('anonymous')
    expect(storage.size).toBe(0)
  })

  it('clears state if a refreshed access token is also rejected, without looping', async () => {
    const { authStore, authenticatedRequest } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(ok(tokens()))
    await authStore.login({ email: user.email, password: 'password123' })
    fetchMock.mockResolvedValueOnce(unauthorized()).mockResolvedValueOnce(ok(tokens('new'))).mockResolvedValueOnce(unauthorized())
    await expect(authenticatedRequest('/users/me')).rejects.toThrow()
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(authStore.getSnapshot().status).toBe('anonymous')
  })
})
