import { ApiError, apiRequest } from '../../api/client'
import { authApi } from './api'
import type { AuthSession, LoginRequest, User } from './types'

const STORAGE_KEY = 'chatsocial.auth'
type Snapshot = {
  status: 'loading' | 'authenticated' | 'anonymous' | 'error'
  session: AuthSession | null
}
let snapshot: Snapshot = { status: 'loading', session: null }
const listeners = new Set<() => void>()
let revision = 0
let refreshPromise: Promise<AuthSession> | null = null
let initialization: Promise<void> | null = null

function publish(next: Snapshot) {
  snapshot = next
  listeners.forEach(listener => listener())
}

function persist(session: AuthSession | null) {
  // Storage can be unavailable in privacy mode; the in-memory session still works.
  try {
    if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(STORAGE_KEY)
  } catch { /* Keep the current tab usable without persistence. */ }
}

function readStored(): AuthSession | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
    if (value && typeof value.accessToken === 'string' && typeof value.refreshToken === 'string'
      && typeof value.user?.id === 'string') return value
  } catch { /* Ignore malformed or inaccessible storage. */ }
  persist(null)
  return null
}

function save(session: AuthSession) {
  persist(session)
  publish({ status: 'authenticated', session })
}

function clear() {
  revision++
  persist(null)
  publish({ status: 'anonymous', session: null })
}

function refresh(): Promise<AuthSession> {
  if (refreshPromise) return refreshPromise
  const current = snapshot.session
  if (!current) return Promise.reject(new ApiError(401))
  const startedAt = revision
  refreshPromise = authApi.refresh(current.refreshToken)
    .then(response => {
      // A late refresh must never restore a session after logout or another login.
      if (startedAt !== revision) throw new ApiError(401)
      save(response.data)
      return response.data
    })
    .catch(error => {
      if (startedAt === revision && error instanceof ApiError && [401, 403].includes(error.status)) clear()
      throw error
    })
    .finally(() => { refreshPromise = null })
  return refreshPromise
}

// Retry a protected JSON request once. Public auth requests never trigger refresh.
export async function authenticatedRequest<T>(
  path: `/${string}`,
  options: RequestInit = {},
) {
  const current = snapshot.session
  if (!current) throw new ApiError(401)
  const startedAt = revision
  try {
    return await apiRequest<T>(path, { ...options, accessToken: current.accessToken })
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || startedAt !== revision) throw error
    // Another request may already have rotated the token while this one was in flight.
    const latest = snapshot.session
    if (!latest) throw error
    const session = latest.accessToken !== current.accessToken ? latest : await refresh()
    try {
      return await apiRequest<T>(path, { ...options, accessToken: session.accessToken })
    } catch (retryError) {
      if (startedAt === revision && retryError instanceof ApiError && retryError.status === 401) clear()
      throw retryError
    }
  }
}

export const authStore = {
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
  getSnapshot: () => snapshot,
  updateFullName(userId: string, fullName: string) {
    if (snapshot.session?.user.id !== userId) return
    save({ ...snapshot.session, user: { ...snapshot.session.user, fullName } })
  },
  async login(request: LoginRequest) {
    const response = await authApi.login(request)
    revision++
    save(response.data)
  },
  initialize(): Promise<void> {
    if (initialization) return initialization
    const current = snapshot.session || readStored()
    if (!current) {
      publish({ status: 'anonymous', session: null })
      return Promise.resolve()
    }
    publish({ status: 'loading', session: current })
    const startedAt = revision
    initialization = authenticatedRequest<User>('/users/me')
      .then(response => {
        if (startedAt === revision && snapshot.session) save({ ...snapshot.session, user: response.data })
      })
      .catch(() => {
        // Network/server failures do not discard a potentially valid refresh token.
        if (startedAt === revision && snapshot.session) publish({ ...snapshot, status: 'error' })
      })
      .finally(() => { initialization = null })
    return initialization
  },
  async logout() {
    // Let an ongoing rotation finish before revoking this session.
    if (refreshPromise) await refreshPromise
    await authenticatedRequest<null>('/auth/logout', { method: 'POST' })
    clear()
  },
}
