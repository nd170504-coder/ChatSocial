import { useSyncExternalStore } from 'react'
import { authStore } from '../session'

export function useAuth() {
  return useSyncExternalStore(authStore.subscribe, authStore.getSnapshot)
}
