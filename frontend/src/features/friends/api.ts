import { authenticatedRequest } from '../auth/session'
import type { FriendRequest, FriendRequestDirection, FriendRequestPage, UserSearchPage } from './types'

export const friendRequestApi = {
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
