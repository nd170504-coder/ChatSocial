import { apiRequest } from '../../api/client'
import type { AuthSession, LoginRequest, RegisterRequest, ResetPasswordRequest, User, VerifyEmailRequest } from './types'

function post<T>(path: `/auth/${string}`, body: unknown) {
  return apiRequest<T>(path, { method: 'POST', body: JSON.stringify(body) })
}

export const authApi = {
  login: (body: LoginRequest) => post<AuthSession>('/auth/login', body),
  register: (body: RegisterRequest) => post<User>('/auth/register', body),
  verifyEmail: (body: VerifyEmailRequest) => post<User>('/auth/verify-email', body),
  resendVerification: (email: string) => post<null>('/auth/resend-verification', { email }),
  forgotPassword: (email: string) => post<null>('/auth/forgot-password', { email }),
  resetPassword: (body: ResetPasswordRequest) => post<null>('/auth/reset-password', body),
  refresh: (refreshToken: string) => post<AuthSession>('/auth/refresh', { refreshToken }),
}
