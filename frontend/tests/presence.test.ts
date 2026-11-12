import { expect, it } from 'vitest'
import { formatLastSeen, parsePresence } from '../src/features/presence/state'

it('accepts an empty snapshot so offline users are removed, and deduplicates online ids', () => {
  expect(parsePresence({ type: 'PRESENCE_SNAPSHOT', onlineUserIds: ['friend', 'friend'] })?.onlineUserIds).toEqual(['friend'])
  expect(parsePresence({ type: 'PRESENCE_SNAPSHOT', onlineUserIds: [] })?.onlineUserIds).toEqual([])
})

it('rejects malformed or unrelated events rather than displaying false online statuses', () => {
  for (const value of [null, {}, { type: 'MESSAGE_CREATED', onlineUserIds: ['friend'] },
    { type: 'PRESENCE_SNAPSHOT', onlineUserIds: ['friend', 123] },
    { type: 'PRESENCE_SNAPSHOT', onlineUserIds: [''] }]) expect(parsePresence(value)).toBeNull()
})

it('keeps server-provided last-seen data and removes friends absent from the next snapshot', () => {
  const snapshot = { type: 'PRESENCE_SNAPSHOT', onlineUserIds: ['online'], friends: [
    { userId: 'online', online: true, lastSeenAt: null },
    { userId: 'offline', online: false, lastSeenAt: '2026-11-12T00:00:00Z' },
    { userId: 'unknown', online: false, lastSeenAt: null },
  ] }
  expect(parsePresence(snapshot)?.friends).toEqual(snapshot.friends)
  expect(parsePresence({ type: 'PRESENCE_SNAPSHOT', onlineUserIds: [], friends: [] })?.friends).toEqual([])
  for (const friend of [{ ...snapshot.friends[1], lastSeenAt: 'bad' }, { ...snapshot.friends[1], online: 'false' }, { ...snapshot.friends[1], online: true }]) {
    expect(parsePresence({ ...snapshot, friends: [snapshot.friends[0], friend] })).toBeNull()
  }
})

it('formats relative activity from the server time without inventing missing times', () => {
  const now = Date.parse('2026-11-12T12:00:00Z')
  expect(formatLastSeen(null, now)).toBe('')
  expect(formatLastSeen('bad', now)).toBe('')
  expect(formatLastSeen('2026-11-12T12:10:00Z', now)).toBe('')
  expect(formatLastSeen('2026-11-12T11:59:40Z', now)).toBe('Vừa hoạt động')
  expect(formatLastSeen('2026-11-12T11:55:00Z', now)).toBe('Hoạt động 5 phút trước')
  expect(formatLastSeen('2026-11-12T10:00:00Z', now)).toBe('Hoạt động 2 giờ trước')
  expect(formatLastSeen('2026-11-10T12:00:00Z', now)).toBe('Hoạt động 2 ngày trước')
})
