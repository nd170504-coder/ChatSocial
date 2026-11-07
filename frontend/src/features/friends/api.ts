import { authenticatedRequest } from '../auth/session'
import type { FriendCount, FriendPage, FriendRequest, FriendRequestDirection, FriendRequestPage, UserSearchPage } from './types'

export const friendsApi = {
  list: (page = 0, signal?: AbortSignal) => authenticatedRequest<FriendPage>(`/friends?page=${page}&size=12`, { signal }),
  count: (userId: string, signal?: AbortSignal) => authenticatedRequest<FriendCount>(
    `/users/${encodeURIComponent(userId)}/friends/count`, { signal },
  ),
}

export const friendRequestApi = {
  accept: (requestId: string) => authenticatedRequest<FriendRequest>(`/friend-requests/${encodeURIComponent(requestId)}/accept`, { method: 'POST' }),
  reject: (requestId: string) => authenticatedRequest<FriendRequest>(`/friend-requests/${encodeURIComponent(requestId)}/reject`, { method: 'POST' }),
  list: (direction: FriendRequestDirection, page = 0, signal?: AbortSignal) => authenticatedRequest<FriendRequestPage>(
    `/friend-requests/${direction}?page=${page}&size=12`, { signal },
  ),
  cancel: (requestId: string) => authenticatedRequest<null>(`/friend-requests/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
  }),
  send: (userId: string) => authenticatedRequest<FriendRequest>('/friend-requests', {
    method: 'POST', body: JSON.stringify({ userId }),
  }),
}

export const friendSearchApi = {
  search: (query: string, page = 0, signal?: AbortSignal) => {
    const params = new URLSearchParams({ q: query, page: String(page), size: '12' })
    return authenticatedRequest<UserSearchPage>(`/users/search?${params}`, { signal })
  },
}
