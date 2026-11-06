import { ApiError } from '../../api/client'
import { validateImage } from '../profile/utils'
import type { PostPrivacy, PostValues } from './types'

export const privacyLabels: Record<PostPrivacy, string> = {
  PUBLIC: 'Công khai', FRIENDS: 'Bạn bè', PRIVATE: 'Chỉ mình tôi',
}

export function validatePostFiles(files: File[]) {
  if (files.length > 5) throw new Error('Mỗi bài viết chỉ được chọn tối đa 5 ảnh.')
  files.forEach(validateImage)
}

export function buildPostValues(content: string, privacy: PostPrivacy, mediaCount: number): PostValues {
  if (content.length > 5000) throw new Error('Nội dung bài viết tối đa 5.000 ký tự.')
  const normalized = content.trim() || null
  if (!normalized && mediaCount === 0) throw new Error('Hãy viết nội dung hoặc chọn ít nhất một ảnh.')
  return { content: normalized, privacy }
}

export function postError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.status === 404) return 'Bài viết không còn tồn tại hoặc bạn không có quyền truy cập.'
    if (error.status === 413) return 'Ảnh vượt quá dung lượng cho phép. Mỗi ảnh tối đa 5 MB.'
    if (error.status === 415) return 'Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP.'
    if (error.status >= 500) return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại sau.'
    return Object.values(error.problem?.errors || {})[0] || error.message
  }
  if (error instanceof TypeError) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối mạng.'
  return error instanceof Error ? error.message : 'Đã có lỗi xảy ra. Vui lòng thử lại.'
}

export function postTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
