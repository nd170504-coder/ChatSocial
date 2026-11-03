import { useEffect, useState } from 'react'
import { authStore } from '../../auth/session'
import { profileApi } from '../api'
import type { Profile } from '../types'
import { profileError } from '../utils'

export function useProfile(userId: string) {
  const [state, setState] = useState<{ profile: Profile | null; loading: boolean; error: string }>({
    profile: null, loading: true, error: '',
  })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void profileApi.get(controller.signal).then(response => {
      if (active) {
        setState({ profile: response.data, loading: false, error: '' })
        authStore.updateFullName(userId, response.data.fullName)
      }
    }).catch(error => {
      if (active) setState({ profile: null, loading: false, error: profileError(error) })
    })
    return () => { active = false; controller.abort() }
  }, [userId, attempt])

  function updateProfile(profile: Profile) {
    setState({ profile, loading: false, error: '' })
    authStore.updateFullName(userId, profile.fullName)
  }

  function retry() {
    setState({ profile: null, loading: true, error: '' })
    setAttempt(value => value + 1)
  }

  return { ...state, updateProfile, retry }
}
