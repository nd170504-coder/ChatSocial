import { ApiError } from '../../api/client'

export function normalizeUserQuery(value: string) {
  const query = value.trim().replace(/^@/, '').toLowerCase()
  if (query.length < 2 || query.length > 255) throw new Error('Nhập từ 2 đến 255 ký tự để tìm kiếm.')
  return query
}

export function searchError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.status >= 500) return 'Chưa thể tìm kiếm lúc này. Vui lòng thử lại sau.'
    return error.message
  }
  return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối mạng.'
}
