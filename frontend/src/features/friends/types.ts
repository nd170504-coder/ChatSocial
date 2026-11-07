export interface UserSearchItem {
  id: string
  username: string
  fullName: string
  avatarUrl: string | null
}

export interface Friend {
  userId: string
  username: string
  fullName: string
  avatarUrl: string | null
}

export interface FriendPage {
  items: Friend[]
  page: number
  size: number
  hasNext: boolean
}

export interface FriendCount {
  userId: string
  friendCount: number
}

export interface UserSearchPage {
  items: UserSearchItem[]
  page: number
  size: number
  hasNext: boolean
}

export interface FriendRequest {
  id: string
  requesterId: string
  addresseeId: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  createdAt: string
  updatedAt: string
}

export type FriendRequestDirection = 'incoming' | 'outgoing'

export interface FriendRequestListItem {
  id: string
  user: UserSearchItem
  status: 'PENDING'
  sentAt: string
}

export interface FriendRequestPage {
  items: FriendRequestListItem[]
  page: number
  size: number
  hasNext: boolean
}
