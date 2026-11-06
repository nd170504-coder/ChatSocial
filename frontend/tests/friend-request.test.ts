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
  for (const [state, label] of [['OUTGOING_PENDING', 'Đã gửi lời mời'], ['INCOMING_PENDING', 'Đã nhận lời mời'], ['FRIENDS', 'Bạn bè']] as const) {
    const markup = render(state)
    expect(markup).toContain(label)
    expect(markup).not.toContain('<button')
  }
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
})
