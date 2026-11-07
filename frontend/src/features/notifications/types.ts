export type NotificationType = 'FRIEND_REQUEST' | 'FRIEND_ACCEPTED' | 'POST_LIKED' | 'POST_COMMENTED'

export interface NotificationItem {
  id: string
  type: NotificationType
  actorId: string
  actorName: string
  actorAvatarUrl: string | null
  postId: string | null
  createdAt: string
  readAt: string | null
}

export interface NotificationPage {
  items: NotificationItem[]
  page: number
  size: number
  hasNext: boolean
}
