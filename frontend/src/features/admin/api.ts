import { authenticatedRequest } from '../auth/session'
import type { User } from '../auth/types'

export interface AdminUser {
  id: string
  username: string
  email: string
  role: User['role']
  status: User['status']
  createdAt: string
  moderationReason: string | null
  moderatedAt: string | null
}

export interface AdminUsers {
  totalUsers: number
  totalMatches: number
  page: number
  hasNext: boolean
  items: AdminUser[]
}

export interface AdminOverview {
  totalUsers: number
  newUsersToday: number
  newUsersInPeriod: number
  timezone: string
  registrations: { date: string; count: number }[]
  statuses: { status: User['status']; count: number }[]
}

export const adminApi = {
  overview: (days: number, signal: AbortSignal) => authenticatedRequest<AdminOverview>(`/admin/overview?days=${days}`, { signal }),
  list: (q: string, page: number, signal: AbortSignal) => authenticatedRequest<AdminUsers>(
    `/admin/users?${new URLSearchParams({ q, page: String(page), size: '20' })}`, { signal }),
  changeStatus: (id: string, suspended: boolean, reason: string) => authenticatedRequest<AdminUser>(
    `/admin/users/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: JSON.stringify({ suspended, reason }) }),
}
