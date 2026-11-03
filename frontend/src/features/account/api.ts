import { authenticatedRequest, authStore } from '../auth/session'
import type { AccountAction, AccountActionRequest, ChangePasswordRequest } from './types'

async function mutate(path: `/${string}`, body: ChangePasswordRequest | AccountActionRequest) {
  const sessionId = authStore.getSnapshot().session?.sessionId
  const response = await authenticatedRequest<null>(path, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  // Only clear local tokens after the backend confirms its transaction succeeded.
  if (sessionId && authStore.getSnapshot().session?.sessionId === sessionId) authStore.endSession()
  return response
}

export const accountApi = {
  changePassword: (body: ChangePasswordRequest) => mutate('/auth/change-password', body),
  changeStatus: (action: AccountAction, body: AccountActionRequest) => mutate(`/users/me/${action}`, body),
}
