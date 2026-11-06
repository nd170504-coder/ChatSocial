export interface UserSearchItem {
  id: string
  username: string
  fullName: string
  avatarUrl: string | null
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
