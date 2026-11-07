import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, expect, it, vi } from 'vitest'
import { FriendRequestAction } from '../src/features/friends/components/FriendRequestAction'
import type { UserProfile, ProfileRelationship } from '../src/features/profile/types'

afterEach(() => vi.unstubAllGlobals())

it('only shows the send button when no friendship or pending invitation exists', () => {
  const profile: UserProfile = { userId: 'target', username: 'target', fullName: 'Target',
    avatarUrl: null, coverUrl: null, bio: null, relationship: 'NONE' }
  const render = (relationship: ProfileRelationship) => renderToStaticMarkup(createElement(FriendRequestAction, {
    profile: { ...profile, relationship }, onUpdated: vi.fn(), onBusyChange: vi.fn(),
  }))
  expect(render('NONE')).toContain('<button')
  expect(render('NONE')).toContain('Kết bạn')
  expect(render('OUTGOING_PENDING')).toContain('Đã gửi lời mời')
  expect(render('OUTGOING_PENDING')).toContain('Hủy lời mời')
  expect(render('INCOMING_PENDING')).toContain('Chấp nhận')
  expect(render('INCOMING_PENDING')).toContain('Từ chối')
  expect(render('FRIENDS')).toContain('Bạn bè')
  expect(render('FRIENDS')).not.toContain('<button')
  expect(render('SELF')).toBe('')
})

it('sends only the recipient userId using the logged-in token', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  const { authStore } = await import('../src/features/auth/session')
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'sender' } }))
  await authStore.login({ email: 'sender@example.com', password: 'password123' })
  fetchMock.mockResolvedValueOnce(response({ id: 'invitation', status: 'PENDING' }))
  const { friendRequestApi } = await import('../src/features/friends/api')
  expect((await friendRequestApi.send('recipient')).data.status).toBe('PENDING')
  const [url, options] = fetchMock.mock.calls[1]
  expect(String(url)).toMatch(/\/api\/friend-requests$/)
  expect(options?.method).toBe('POST')
  expect(JSON.parse(options?.body as string)).toEqual({ userId: 'recipient' })
  expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
  fetchMock.mockResolvedValueOnce(response(null))
  await friendRequestApi.cancel('invitation')
  const [cancelUrl, cancelOptions] = fetchMock.mock.calls[2]
  expect(String(cancelUrl)).toMatch(/\/api\/friend-requests\/invitation$/)
  expect(cancelOptions?.method).toBe('DELETE')
  expect(cancelOptions?.body).toBeUndefined()
  expect(new Headers(cancelOptions?.headers).get('Authorization')).toBe('Bearer access')
  for (const action of ['accept', 'reject'] as const) {
    fetchMock.mockResolvedValueOnce(response({ id: 'invitation', status: action === 'accept' ? 'ACCEPTED' : 'REJECTED' }))
    await friendRequestApi[action]('invitation')
    const [actionUrl, actionOptions] = fetchMock.mock.calls.at(-1)!
    expect(String(actionUrl)).toMatch(new RegExp(`/api/friend-requests/invitation/${action}$`))
    expect(actionOptions?.method).toBe('POST')
    expect(actionOptions?.body).toBeUndefined()
    expect(new Headers(actionOptions?.headers).get('Authorization')).toBe('Bearer access')
  }
})

it('loads the incoming/outgoing pages with authentication, pagination and cancellation signals', async () => {
  vi.resetModules()
  const fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} })
  const response = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'OK', data }))
  const { authStore } = await import('../src/features/auth/session')
  fetchMock.mockResolvedValueOnce(response({ accessToken: 'access', refreshToken: 'refresh', user: { id: 'viewer' } }))
  await authStore.login({ email: 'viewer@example.com', password: 'password123' })
  fetchMock.mockImplementation(async () => response({ items: [], page: 2, size: 12, hasNext: false }))
  const { friendRequestApi } = await import('../src/features/friends/api')
  const controller = new AbortController()
  for (const direction of ['incoming', 'outgoing'] as const) {
    await friendRequestApi.list(direction, 2, controller.signal)
    const [url, options] = fetchMock.mock.calls.at(-1)!
    expect(String(url)).toContain(`/friend-requests/${direction}?page=2&size=12`)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access')
    expect(options?.signal).toBe(controller.signal)
  }
})
