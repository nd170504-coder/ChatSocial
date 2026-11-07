import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildProfileUpdate, latestBirthDate, validateImage } from '../src/features/profile/utils'

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

function values(fields: Record<string, string>) {
  const form = new FormData()
  Object.entries(fields).forEach(([key, value]) => form.set(key, value))
  return form
}

describe('Profile editing contract', () => {
  it('preserves all editable fields in the replacement PATCH payload', () => {
    expect(buildProfileUpdate(values({ fullName: ' Nguyễn Minh Anh ', bio: 'Mê cà phê', dateOfBirth: '2000-02-29', gender: 'OTHER' })))
      .toEqual({ fullName: 'Nguyễn Minh Anh', bio: 'Mê cà phê', dateOfBirth: '2000-02-29', gender: 'OTHER' })
  })

  it('explicitly sends null for cleared optional fields', () => {
    expect(buildProfileUpdate(values({ fullName: 'Minh Anh', bio: '   ', dateOfBirth: '', gender: '' })))
      .toEqual({ fullName: 'Minh Anh', bio: null, dateOfBirth: null, gender: null })
  })

  it('rejects a whitespace-only name instead of saving an empty name', () => {
    expect(() => buildProfileUpdate(values({ fullName: '   ' }))).toThrow('Vui lòng nhập họ và tên.')
  })

  it('accepts the fifteenth birthday and rejects a younger user', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 3, 12))
    expect(buildProfileUpdate(values({ fullName: 'Minh', dateOfBirth: '2011-11-03' })).dateOfBirth).toBe('2011-11-03')
    expect(() => buildProfileUpdate(values({ fullName: 'Minh', dateOfBirth: '2011-11-04' }))).toThrow('15 tuổi')
  })

  it('clamps a leap-day age cutoff like LocalDate.minusYears on the backend', () => {
    expect(latestBirthDate(new Date(2024, 1, 29, 12))).toBe('2009-02-28')
  })
})

describe('Profile image limits', () => {
  it('accepts exactly 5 MB and rejects larger files', () => {
    expect(() => validateImage(new File([new Uint8Array(5 * 1024 * 1024)], 'photo.png', { type: 'image/png' }))).not.toThrow()
    expect(() => validateImage(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'photo.png', { type: 'image/png' }))).toThrow('5 MB')
  })

  it('rejects SVG and empty images before upload', () => {
    expect(() => validateImage(new File(['<svg/>'], 'photo.svg', { type: 'image/svg+xml' }))).toThrow('JPEG, PNG hoặc WebP')
    expect(() => validateImage(new File([], 'photo.jpg', { type: 'image/jpeg' }))).toThrow('trống')
  })
})

describe('Authenticated multipart upload', () => {
  const fetchMock = vi.fn<typeof fetch>()
  beforeEach(() => {
    vi.resetModules()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  })

  it('loads another profile through the authenticated user-specific endpoint', async () => {
    const { authStore } = await import('../src/features/auth/session')
    const { profileApi } = await import('../src/features/profile/api')
    const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
    fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
    await authStore.login({ email: 'viewer@example.com', password: 'password123' })
    fetchMock.mockResolvedValueOnce(response({ userId: 'author', username: 'author', relationship: 'NONE' }))
    const controller = new AbortController()
    expect((await profileApi.getUser('author', controller.signal)).data.relationship).toBe('NONE')
    const [url, options] = fetchMock.mock.calls[1]
    expect(String(url)).toMatch(/\/users\/author\/profile$/)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
    expect(options?.signal).toBe(controller.signal)
  })

  it('reuses the file after token rotation without manually setting a multipart boundary', async () => {
    const { authStore } = await import('../src/features/auth/session')
    const { profileApi } = await import('../src/features/profile/api')
    const user = { id: 'user-1', fullName: 'Minh', username: 'minh', email: 'test@example.com', status: 'ACTIVE' }
    const session = { accessToken: 'old-access', refreshToken: 'old-refresh', user }
    const profile = { userId: user.id, fullName: user.fullName, avatarUrl: '/photo.png', coverUrl: null, bio: null, dateOfBirth: null, gender: null }
    const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
    fetchMock.mockResolvedValueOnce(response(session))
    await authStore.login({ email: user.email, password: 'test-password' })
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 401 }))
      .mockResolvedValueOnce(response({ ...session, accessToken: 'new-access', refreshToken: 'new-refresh' }))
      .mockResolvedValueOnce(response(profile))
    const file = new File(['image bytes'], 'avatar.png', { type: 'image/png' })
    expect((await profileApi.upload('avatar', file)).data.avatarUrl).toBe('/photo.png')
    const uploads = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/profile/avatar'))
    expect(uploads).toHaveLength(2)
    for (const [, options] of uploads) {
      expect(options?.body).toBeInstanceOf(FormData)
      expect((options?.body as FormData).get('file')).toBe(file)
      expect(new Headers(options?.headers).has('Content-Type')).toBe(false)
    }
    expect(new Headers(uploads[1][1]?.headers).get('Authorization')).toBe('Bearer new-access')
  })
})
