import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../../api/client'
import { errorMessage } from '../errors'

export function useAuthForm() {
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
      setError(cause instanceof Error && !(cause instanceof ApiError) && cause.name === 'Error'
        ? cause.message : errorMessage(cause))
      if (cause instanceof ApiError) setFieldErrors(cause.problem?.errors || {})
    } finally { locked.current = false; setBusy(false) }
  }

  function submit(action: (values: FormData) => Promise<void>) {
    return (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      const values = new FormData(event.currentTarget)
      void run(() => action(values))
    }
  }

  return { busy, error, fieldErrors, run, submit }
}
