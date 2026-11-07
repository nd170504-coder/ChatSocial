import { ApiError } from '../../api/client'
import type { ProfileRelationship } from '../profile/types'

export const relationshipLabels: Record<ProfileRelationship, string> = {
  SELF: 'Hồ sơ của tôi', NONE: 'Kết bạn', FRIENDS: 'Bạn bè',
  OUTGOING_PENDING: 'Đã gửi lời mời', INCOMING_PENDING: 'Đã nhận lời mời',
}

export function friendRequestError(error: unknown, action: 'send' | 'cancel' | 'accept' | 'reject' = 'send') {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.status === 404) return action !== 'send' ? 'Lời mời không còn khả dụng. Hãy làm mới để kiểm tra.' : 'Tài khoản này không còn khả dụng.'
    if (error.status === 409) return 'Quan hệ đã thay đổi. Hãy làm mới hồ sơ để kiểm tra.'
    if (error.status === 400) return 'Không thể thực hiện thao tác với lời mời này.'
    if (error.status >= 500) {
      const labels = { send: 'gửi', cancel: 'hủy', accept: 'chấp nhận', reject: 'từ chối' }
      return `Chưa thể ${labels[action]} lời mời. Vui lòng thử lại sau.`
    }
    return error.message
  }
  return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
}

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
