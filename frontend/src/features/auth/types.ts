export interface User {
  id: string
  email: string
  username: string
  fullName: string
  role: 'USER' | 'ADMIN'
  status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'DISABLED' | 'SUSPENDED' | 'DELETED'
}

export interface LoginRequest { email: string; password: string }
export interface RegisterRequest extends LoginRequest { username: string; fullName: string }
export interface VerifyEmailRequest { email: string; otp: string }
export interface ResetPasswordRequest extends VerifyEmailRequest {
  newPassword: string
  confirmPassword: string
}
export interface AuthSession {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  sessionId: string
  user: User
}
