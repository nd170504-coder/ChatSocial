export type FriendPresence = { userId: string; online: boolean; lastSeenAt: string | null }
export type PresenceSnapshot = { type: 'PRESENCE_SNAPSHOT'; onlineUserIds: string[]; friends: FriendPresence[] }

export function parsePresence(value: unknown): PresenceSnapshot | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Partial<PresenceSnapshot>
  if (item.type !== 'PRESENCE_SNAPSHOT' || !Array.isArray(item.onlineUserIds)
    || !item.onlineUserIds.every(id => typeof id === 'string' && id.length > 0)) return null
  const onlineUserIds = [...new Set(item.onlineUserIds)]
  // Allow older snapshots to keep showing online dots, without inventing last-seen times.
  if (item.friends === undefined) return { type: item.type, onlineUserIds, friends: onlineUserIds.map(userId => ({ userId, online: true, lastSeenAt: null })) }
  if (!Array.isArray(item.friends)) return null
  const friends: FriendPresence[] = [], ids = new Set<string>()
  for (const friend of item.friends) {
    if (!friend || typeof friend.userId !== 'string' || !friend.userId || ids.has(friend.userId)
      || typeof friend.online !== 'boolean' || friend.online !== onlineUserIds.includes(friend.userId)
      || (friend.lastSeenAt !== null && (typeof friend.lastSeenAt !== 'string' || !Number.isFinite(Date.parse(friend.lastSeenAt))))) return null
    ids.add(friend.userId)
    friends.push({ userId: friend.userId, online: friend.online, lastSeenAt: friend.online ? null : friend.lastSeenAt })
  }
  if (onlineUserIds.some(id => !ids.has(id))) return null
  return { type: item.type, onlineUserIds, friends }
}

export function formatLastSeen(lastSeenAt: string | null, now = Date.now()): string {
  if (!lastSeenAt) return ''
  const time = Date.parse(lastSeenAt)
  if (!Number.isFinite(time) || time > now + 60_000) return ''
  const minutes = Math.floor(Math.max(0, now - time) / 60_000)
  if (minutes < 1) return 'Vừa hoạt động'
  if (minutes < 60) return `Hoạt động ${minutes} phút trước`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `Hoạt động ${hours} giờ trước`
  const days = Math.floor(hours / 24)
  if (days < 7) return `Hoạt động ${days} ngày trước`
  return `Hoạt động ngày ${new Intl.DateTimeFormat('vi-VN').format(new Date(time))}`
}
