import { authenticatedRequest } from '../auth/session'
import type { NotificationItem, NotificationPage } from './types'

export const notificationsApi = {
  list: (page = 0, signal?: AbortSignal) => authenticatedRequest<NotificationPage>(`/notifications?page=${page}&size=20`, { signal }),
  unread: (signal?: AbortSignal) => authenticatedRequest<{ unreadCount: number }>('/notifications/unread-count', { signal }),
  read: (id: string) => authenticatedRequest<NotificationItem>(`/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' }),
  readAll: () => authenticatedRequest<{ unreadCount: number }>('/notifications/read-all', { method: 'PATCH' }),
}
