import { useEffect, useState } from 'react'

export function useCooldown(initialSeconds = 0) {
  const [seconds, setSeconds] = useState(initialSeconds)
  useEffect(() => {
    if (!seconds) return
    const timer = window.setTimeout(() => setSeconds(value => Math.max(0, value - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [seconds])
  return { seconds, start: () => setSeconds(60) }
}
