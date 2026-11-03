export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

// Backend trả lỗi theo định dạng Spring ProblemDetail.
export interface ApiProblem {
  title?: string
  detail?: string
  status?: number
  errors?: Record<string, string>
}
