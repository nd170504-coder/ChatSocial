import { expect, it } from 'vitest'
import { parsePresence } from '../src/features/presence/state'

it('accepts an empty snapshot so offline users are removed, and deduplicates online ids', () => {
  expect(parsePresence({ type: 'PRESENCE_SNAPSHOT', onlineUserIds: ['friend', 'friend'] })?.onlineUserIds).toEqual(['friend'])
  expect(parsePresence({ type: 'PRESENCE_SNAPSHOT', onlineUserIds: [] })?.onlineUserIds).toEqual([])
})

it('rejects malformed or unrelated events rather than displaying false online statuses', () => {
  for (const value of [null, {}, { type: 'MESSAGE_CREATED', onlineUserIds: ['friend'] },
    { type: 'PRESENCE_SNAPSHOT', onlineUserIds: ['friend', 123] },
    { type: 'PRESENCE_SNAPSHOT', onlineUserIds: [''] }]) expect(parsePresence(value)).toBeNull()
})
