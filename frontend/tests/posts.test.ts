import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildPostValues, validatePostFiles } from '../src/features/posts/utils'

afterEach(() => { vi.unstubAllGlobals() })

describe('Post validation', () => {
  it('supports text, images or both and rejects an empty post', () => {
    expect(buildPostValues(' Hello ', 'PUBLIC', 0)).toEqual({ content: 'Hello', privacy: 'PUBLIC' })
    expect(buildPostValues('  ', 'FRIENDS', 1)).toEqual({ content: null, privacy: 'FRIENDS' })
    expect(() => buildPostValues('', 'PRIVATE', 0)).toThrow('ít nhất một ảnh')
    expect(() => buildPostValues('a'.repeat(5001), 'FRIENDS', 1)).toThrow('5.000')
  })

  it('limits image count and validates every file before upload', () => {
    const file = new File(['png'], 'image.png', { type: 'image/png' })
    expect(() => validatePostFiles(Array(5).fill(file))).not.toThrow()
    expect(() => validatePostFiles(Array(6).fill(file))).toThrow('5 ảnh')
    expect(() => validatePostFiles([new File(['svg'], 'image.svg', { type: 'image/svg+xml' })])).toThrow('JPEG')
    expect(() => validatePostFiles([new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' })])).toThrow('5 MB')
  })
})

describe('Post API contract', () => {
  const fetchMock = vi.fn<typeof fetch>()
  const user = { id: 'user-1', fullName: 'Minh', username: 'minh', email: 'test@example.com', status: 'ACTIVE' }
  const session = { accessToken: 'old-access', refreshToken: 'old-refresh', user }
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))

  beforeEach(() => {
    vi.resetModules()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  })

  async function login() {
    const { authStore } = await import('../src/features/auth/session')
    fetchMock.mockResolvedValueOnce(response(session))
    await authStore.login({ email: user.email, password: 'test-password' })
    return authStore
  }

  it('sends post JSON as an application/json part and repeated image parts without setting the boundary', async () => {
    await login()
    const { postApi } = await import('../src/features/posts/api')
    fetchMock.mockResolvedValueOnce(response({ id: 'post-1' }))
    const file = new File(['png'], 'image.png', { type: 'image/png' })
    await postApi.create({ content: null, privacy: 'FRIENDS' }, [file, file])
    const [url, options] = fetchMock.mock.calls[1]
    expect(String(url)).toMatch(/\/api\/post$/)
    const body = options?.body as FormData
    const part = body.get('post') as Blob
    expect(part.type).toBe('application/json')
    expect(JSON.parse(await part.text())).toEqual({ content: null, privacy: 'FRIENDS' })
    expect(body.getAll('files')).toEqual([file, file])
    expect(new Headers(options?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer old-access')
  })

  it('rotates an expired token once and returns the private image as a Blob', async () => {
    await login()
    const { postApi } = await import('../src/features/posts/api')
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 401 }))
      .mockResolvedValueOnce(response({ ...session, accessToken: 'new-access', refreshToken: 'new-refresh' }))
      .mockResolvedValueOnce(new Response(new Uint8Array([1, 2, 3]), { headers: { 'Content-Type': 'image/png' } }))
    const image = await postApi.image('post-1', 'media-1')
    expect(image).toBeInstanceOf(Blob)
    expect(image.type).toBe('image/png')
    const downloads = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/post/post-1/media/media-1'))
    expect(downloads).toHaveLength(2)
    expect(new Headers(downloads[1][1]?.headers).get('Authorization')).toBe('Bearer new-access')
    expect(new Headers(downloads[1][1]?.headers).get('Accept')).toContain('application/octet-stream')
  })

  it('uses the backend pagination and PATCH/DELETE endpoints', async () => {
    await login()
    const { postApi } = await import('../src/features/posts/api')
    fetchMock.mockImplementation(async () => response(null))
    await postApi.list(user.id, 2)
    await postApi.update('post-1', { content: null, privacy: 'PRIVATE' })
    await postApi.remove('post-1')
    await postApi.publicList(1)
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/users\/user-1\/posts\?page=2&size=10$/)
    expect(fetchMock.mock.calls[2][1]?.method).toBe('PATCH')
    expect(JSON.parse(fetchMock.mock.calls[2][1]?.body as string)).toEqual({ content: null, privacy: 'PRIVATE' })
    expect(fetchMock.mock.calls[3][1]?.method).toBe('DELETE')
    expect(String(fetchMock.mock.calls[4][0])).toMatch(/\/post\/public\?page=1&size=10$/)
  })
})
