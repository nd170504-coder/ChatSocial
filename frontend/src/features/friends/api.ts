import { authenticatedRequest } from '../auth/session'
import type { FriendRequest, UserSearchPage } from './types'

export const friendRequestApi = {
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
