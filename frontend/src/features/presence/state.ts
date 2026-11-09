export type PresenceSnapshot = { type: 'PRESENCE_SNAPSHOT'; onlineUserIds: string[] }

export function parsePresence(value: unknown): PresenceSnapshot | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Partial<PresenceSnapshot>
  return item.type === 'PRESENCE_SNAPSHOT' && Array.isArray(item.onlineUserIds)
    && item.onlineUserIds.every(id => typeof id === 'string' && id.length > 0)
    ? { type: item.type, onlineUserIds: [...new Set(item.onlineUserIds)] } : null
}
