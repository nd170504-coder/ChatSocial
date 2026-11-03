import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildPasswordChange, validateCurrentPassword } from '../src/features/account/utils'

afterEach(() => { vi.unstubAllGlobals() })

function passwordForm(current: string, next: string, confirmation = next) {
  const data = new FormData()
  data.set('currentPassword', current)
  data.set('newPassword', next)
  data.set('confirmPassword', confirmation)
  return data
}

describe('Account password validation', () => {
  it('preserves password spaces instead of trimming credentials', () => {
    expect(buildPasswordChange(passwordForm(' old password ', ' new password ')))
      .toEqual({ currentPassword: ' old password ', newPassword: ' new password ', confirmPassword: ' new password ' })
  })
  it('rejects mismatch, unchanged and whitespace-only passwords', () => {
    expect(() => buildPasswordChange(passwordForm('old-password', 'new-password', 'different'))).toThrow('chưa khớp')
    expect(() => buildPasswordChange(passwordForm('old-password', 'old-password'))).toThrow('phải khác')
    expect(() => buildPasswordChange(passwordForm('old-password', '        '))).toThrow('khoảng trắng')
  })
  it('enforces the BCrypt byte limit for multibyte input', () => {
    expect(() => validateCurrentPassword('ắ'.repeat(25))).toThrow('72 byte')
    expect(() => buildPasswordChange(passwordForm('old-password', 'ắ'.repeat(25)))).toThrow('72 byte')
  })
})

describe('Account session revocation', () => {
  const fetchMock = vi.fn<typeof fetch>()
  let storage: Map<string, string>
  const user = { id: 'user-1', email: 'demo@example.test', username: 'demo', fullName: 'Demo', status: 'ACTIVE' }
  const session = { accessToken: 'access-old', refreshToken: 'refresh-old', sessionId: 'session-1', user }
  const ok = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  beforeEach(() => {
    vi.resetModules()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    storage = new Map()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })
  })

  async function login() {
    const { authStore } = await import('../src/features/auth/session')
    const { accountApi } = await import('../src/features/account/api')
    fetchMock.mockResolvedValueOnce(ok(session))
    await authStore.login({ email: user.email, password: 'old-password' })
    return { authStore, accountApi }
  }

  it.each(['change-password', 'deactivate', 'delete'] as const)('clears tokens only after %s succeeds, without a logout call', async action => {
    const { authStore, accountApi } = await login()
    fetchMock.mockResolvedValueOnce(ok(null))
    if (action === 'change-password') await accountApi.changePassword({ currentPassword: 'old-password', newPassword: 'new-password', confirmPassword: 'new-password' })
    else await accountApi.changeStatus(action, { currentPassword: 'old-password' })
    expect(authStore.getSnapshot()).toEqual({ status: 'anonymous', session: null })
    expect(storage.size).toBe(0)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.lastCall?.[0]).toMatch(new RegExp(`/${action}$`))
    expect(fetchMock.mock.lastCall?.[1]?.method).toBe('POST')
    expect(new Headers(fetchMock.mock.lastCall?.[1]?.headers).get('Authorization')).toBe('Bearer access-old')
  })

  it('keeps the session when the current password is wrong', async () => {
    const { authStore, accountApi } = await login()
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'Current password is incorrect' }), { status: 400 }))
    await expect(accountApi.changeStatus('delete', { currentPassword: 'wrong-password' })).rejects.toThrow('Current password')
    expect(authStore.getSnapshot().status).toBe('authenticated')
    expect(storage.size).toBe(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('keeps the session on a network failure without claiming success', async () => {
    const { authStore, accountApi } = await login()
    fetchMock.mockRejectedValueOnce(new TypeError('Network error'))
    await expect(accountApi.changeStatus('deactivate', { currentPassword: 'old-password' })).rejects.toThrow()
    expect(authStore.getSnapshot().status).toBe('authenticated')
    expect(storage.size).toBe(1)
  })

  it('does not clear a different session if a previous account request finishes late', async () => {
    const { authStore, accountApi } = await login()
    let complete!: (response: Response) => void
    fetchMock.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const pending = accountApi.changeStatus('deactivate', { currentPassword: 'old-password' })
    fetchMock.mockResolvedValueOnce(ok({ ...session, sessionId: 'session-2', user: { ...user, id: 'user-2' } }))
    await authStore.login({ email: 'another@example.test', password: 'another-password' })
    complete(ok(null))
    await pending
    expect(authStore.getSnapshot().session?.sessionId).toBe('session-2')
  })
})
