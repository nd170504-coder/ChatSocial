export function tokenExpiresAt(token: string): number | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const { exp } = JSON.parse(atob(payload))
    return typeof exp === 'number' && Number.isFinite(exp) ? exp * 1000 : null
  } catch { return null }
}
