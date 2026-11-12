import { authenticatedRequest } from '../auth/session'

export interface UserStatistics {
  postCount: number
  friendCount: number
  receivedLikeCount: number
  receivedCommentCount: number
}

export const statisticsApi = {
  get: (signal?: AbortSignal) => authenticatedRequest<UserStatistics>('/users/me/statistics', { signal }),
}
