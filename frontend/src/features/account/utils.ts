import { ApiError } from '../../api/client'
import { validatePassword } from '../auth/errors'
import type { ChangePasswordRequest } from './types'

export function validateCurrentPassword(password: string) {
  if (!password.trim()) throw new Error('Vui lòng nhập mật khẩu hiện tại.')
  if (new TextEncoder().encode(password).length > 72) throw new Error('Mật khẩu vượt quá giới hạn 72 byte.')
}

export function buildPasswordChange(values: FormData): ChangePasswordRequest {
  const currentPassword = String(values.get('currentPassword') || '')
  const newPassword = String(values.get('newPassword') || '')
  const confirmPassword = String(values.get('confirmPassword') || '')
  validateCurrentPassword(currentPassword)
  validatePassword(newPassword, confirmPassword)
  if (!newPassword.trim()) throw new Error('Mật khẩu mới không được chỉ chứa khoảng trắng.')
  if (newPassword === currentPassword) throw new Error('Mật khẩu mới phải khác mật khẩu hiện tại.')
  return { currentPassword, newPassword, confirmPassword }
}

export function accountError(error: unknown) {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      'Current password is incorrect': 'Mật khẩu hiện tại chưa đúng. Vui lòng kiểm tra lại.',
      'Password confirmation does not match': 'Mật khẩu xác nhận chưa khớp.',
      'New password must differ from current password': 'Mật khẩu mới phải khác mật khẩu hiện tại.',
      'Request validation failed': 'Vui lòng kiểm tra lại thông tin đã nhập.',
      "Password exceeds BCrypt's supported byte length": 'Mật khẩu vượt quá giới hạn 72 byte.',
    }
    if (messages[error.message]) return messages[error.message]
    if (error.status >= 500) return 'Hệ thống chưa thể xử lý yêu cầu. Vui lòng thử lại sau.'
    return error.message
  }
  if (error instanceof Error && error.name === 'Error') return error.message
  return 'Không nhận được xác nhận từ máy chủ. Kiểm tra kết nối và thử lại; nếu phiên đã hết hiệu lực, hãy đăng nhập lại.'
}
