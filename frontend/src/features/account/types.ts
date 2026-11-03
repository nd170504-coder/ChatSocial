export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export type AccountAction = 'deactivate' | 'delete'

export interface AccountActionRequest {
  currentPassword: string
}
