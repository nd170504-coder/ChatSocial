const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

if (!apiBaseUrl) {
  throw new Error('Thiếu VITE_API_BASE_URL. Hãy cấu hình frontend/.env.local.')
}

export const env = {
  apiBaseUrl: apiBaseUrl.replace(/\/+$/, ''),
}
