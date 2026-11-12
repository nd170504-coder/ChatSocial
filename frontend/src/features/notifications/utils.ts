import type { NotificationItem, NotificationType } from './types'

const descriptions: Record<NotificationType, string> = {
  FRIEND_REQUEST: 'đã gửi lời mời kết bạn cho bạn.',
  FRIEND_ACCEPTED: 'đã chấp nhận lời mời kết bạn của bạn.',
  POST_LIKED: 'đã thích bài viết của bạn.',
  POST_COMMENTED: 'đã bình luận trong một bài viết.',
}

export function notificationText(item: NotificationItem) { return `${item.actorName} ${descriptions[item.type]}` }
export function notificationDescription(item: NotificationItem) { return descriptions[item.type] }
export function notificationTarget(item: NotificationItem) {
  return item.postId && (item.type === 'POST_LIKED' || item.type === 'POST_COMMENTED')
    ? `/posts/${encodeURIComponent(item.postId)}` : `/users/${encodeURIComponent(item.actorId)}`
}

export function parseNotification(body: string): NotificationItem | null {
  try {
    const item = JSON.parse(body)
    if (item && typeof item.id === 'string' && typeof item.actorId === 'string'
      && typeof item.actorName === 'string' && Object.hasOwn(descriptions, item.type)
      && typeof item.createdAt === 'string' && Number.isFinite(Date.parse(item.createdAt))
      && (item.readAt === null || typeof item.readAt === 'string')
      && (item.postId === null || typeof item.postId === 'string')
      && (item.actorAvatarUrl === null || typeof item.actorAvatarUrl === 'string')) return item
  } catch { /* Ignore malformed frames without exposing their contents. */ }
  return null
}

export function notificationSocketUrl(apiBaseUrl: string, pageUrl: string) {
  const url = new URL(apiBaseUrl, pageUrl)
  // The backend registers /ws at the application root, outside /api.
  url.pathname = `${url.pathname.replace(/\/api\/?$/, '').replace(/\/$/, '')}/ws`
  url.search = ''; url.hash = ''
  url.username = ''; url.password = ''
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}
