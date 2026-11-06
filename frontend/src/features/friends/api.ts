import { authenticatedRequest } from '../auth/session'
import type { UserSearchPage } from './types'

export const friendSearchApi = {
  search: (query: string, page = 0, signal?: AbortSignal) => {
    const params = new URLSearchParams({ q: query, page: String(page), size: '12' })
    return authenticatedRequest<UserSearchPage>(`/users/search?${params}`, { signal })
  },
}
