import { ApiError } from '../../api/client'

const messages: Record<string, string> = {
  'Invalid email or password': 'Email hoặc mật khẩu chưa đúng.',
  'Email verification is required': 'Bạn cần xác minh email trước khi đăng nhập.',
  'Account is not active': 'Tài khoản hiện không hoạt động.',
  'Email or username already exists': 'Email hoặc tên người dùng đã được sử dụng.',
  'Invalid or expired verification code': 'Mã xác minh không đúng hoặc đã hết hạn.',
  'Invalid or expired reset code': 'Mã đặt lại mật khẩu không đúng hoặc đã hết hạn.',
  'Too many verification attempts; request a new code': 'Bạn đã nhập sai quá nhiều lần. Hãy yêu cầu mã mới.',
  'Password confirmation does not match': 'Mật khẩu xác nhận chưa khớp.',
  'New password must differ from current password': 'Mật khẩu mới phải khác mật khẩu hiện tại.',
  'Request validation failed': 'Vui lòng kiểm tra lại thông tin đã nhập.',
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return messages[error.message] || (error.status === 429
      ? 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.'
      : error.status >= 500 ? 'Hệ thống đang bận. Vui lòng thử lại sau.' : error.message)
  }
  return 'Không thể kết nối máy chủ. Kiểm tra kết nối mạng và thử lại.'
}

export function validatePassword(password: string, confirmation?: string) {
  if (password.length < 8) throw new Error('Mật khẩu cần ít nhất 8 ký tự.')
  if (new TextEncoder().encode(password).length > 72) throw new Error('Mật khẩu vượt quá giới hạn 72 byte. Hãy dùng mật khẩu ngắn hơn.')
  if (confirmation !== undefined && password !== confirmation) throw new Error('Mật khẩu xác nhận chưa khớp.')
}
