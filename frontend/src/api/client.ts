import { env } from '../config/env'
import type { ApiProblem, ApiResponse } from '../types/api'

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ApiProblem

  constructor(status: number, problem?: ApiProblem) {
    super(problem?.detail || problem?.title || `Yêu cầu thất bại (HTTP ${status}).`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

interface ApiRequestOptions extends RequestInit {
  accessToken?: string
}

// path tính từ /api, ví dụ: /auth/login hoặc /users/me.
export async function apiRequest<T>(
  path: `/${string}`,
  options: ApiRequestOptions = {},
): Promise<ApiResponse<T>> {
  const { accessToken, ...requestOptions } = options
  const headers = new Headers(requestOptions.headers)
  headers.set('Accept', 'application/json')

  if (typeof requestOptions.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  // Không tự đặt Content-Type cho FormData: trình duyệt cần thêm boundary.
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...requestOptions,
    headers,
  })

  if (!response.ok) {
    let problem: ApiProblem | undefined
    try {
      const body: unknown = await response.json()
      if (body && typeof body === 'object') problem = body as ApiProblem
    } catch {
      // Một số proxy trả lỗi không có JSON; vẫn giữ HTTP status.
    }
    throw new ApiError(response.status, problem)
  }

  return response.json() as Promise<ApiResponse<T>>
}
