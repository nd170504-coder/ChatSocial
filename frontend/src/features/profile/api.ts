import { authenticatedRequest } from '../auth/session'
import type { Profile, ProfileImageType, UpdateProfileRequest, UserProfile } from './types'

export const profileApi = {
  getUser: (userId: string, signal?: AbortSignal) => authenticatedRequest<UserProfile>(
    `/users/${encodeURIComponent(userId)}/profile`, { signal },
  ),
  get: (signal?: AbortSignal) => authenticatedRequest<Profile>('/users/me/profile', { signal }),
  update: (body: UpdateProfileRequest) => authenticatedRequest<Profile>('/users/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(body),
  }),
  upload: (type: ProfileImageType, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return authenticatedRequest<Profile>(`/users/me/profile/${type}`, { method: 'POST', body })
  },
}
