import { useRef, useState } from 'react'
import { ApiError } from '../../../api/client'
import { accountError } from '../utils'

export function useAccountForm() {
  const locked = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function run(action: () => Promise<void>) {
    if (locked.current) return
    locked.current = true
    setBusy(true)
    setError('')
    setFieldErrors({})
    try { await action() } catch (cause) {
      setError(accountError(cause))
      if (cause instanceof ApiError) setFieldErrors(cause.problem?.errors || {})
    } finally { locked.current = false; setBusy(false) }
  }

  return { busy, error, fieldErrors, run, isBusy: () => locked.current }
}
