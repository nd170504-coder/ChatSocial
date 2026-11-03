import { ApiError } from '../../api/client'
import type { Gender, UpdateProfileRequest } from './types'

export const genderLabels: Record<Gender, string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
  PREFER_NOT_TO_SAY: 'Không muốn chia sẻ',
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function latestBirthDate(now = new Date()) {
  const year = now.getFullYear() - 15
  const month = now.getMonth()
  const day = Math.min(now.getDate(), new Date(year, month + 1, 0).getDate())
  return localDate(new Date(year, month, day))
}

export function formatBirthDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(year, month - 1, day))
}

export function initials(name: string) {
  return name.trim().split(/\s+/).slice(-2).map(word => word[0] || '').join('').toUpperCase() || '?'
}

// PATCH replaces all editable fields on the backend, so always send every field.
export function buildProfileUpdate(values: FormData): UpdateProfileRequest {
  const fullName = String(values.get('fullName') || '').trim()
  const bio = String(values.get('bio') || '').trim() || null
  const dateOfBirth = String(values.get('dateOfBirth') || '') || null
  const gender = String(values.get('gender') || '') || null
  if (!fullName) throw new Error('Vui lòng nhập họ và tên.')
  if (fullName.length > 100) throw new Error('Họ và tên tối đa 100 ký tự.')
  if (bio && bio.length > 1000) throw new Error('Giới thiệu tối đa 1.000 ký tự.')
  if (dateOfBirth && dateOfBirth > latestBirthDate()) throw new Error('Bạn phải đủ 15 tuổi trở lên.')
  if (gender && !(gender in genderLabels)) throw new Error('Giới tính không hợp lệ.')
  return { fullName, bio, dateOfBirth, gender: gender as Gender | null }
}

export function validateImage(file: File) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw new Error('Vui lòng chọn ảnh JPEG, PNG hoặc WebP.')
  }
  if (!file.size) throw new Error('File ảnh đang trống. Hãy chọn ảnh khác.')
  if (file.size > 5 * 1024 * 1024) throw new Error('Ảnh không được lớn hơn 5 MB.')
}

export function profileError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 413) return 'Ảnh không được lớn hơn 5 MB.'
    if (error.status === 404) return 'Chưa tìm thấy hồ sơ của bạn. Vui lòng thử lại.'
    if (error.status >= 500) return 'Chưa thể lưu dữ liệu. Vui lòng thử lại sau.'
    if (error.problem?.detail === 'Request validation failed') return 'Vui lòng kiểm tra các thông tin bên dưới.'
    return error.message
  }
  if (error instanceof Error && error.name === 'Error') return error.message
  return 'Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.'
}
