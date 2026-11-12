export interface TypingEvent { conversationId: string; userId: string; typing: boolean }

export function parseTypingEvent(body: string): TypingEvent | null {
  try {
    const event = JSON.parse(body)
    if (event && typeof event.conversationId === 'string' && event.conversationId.length > 0
      && typeof event.userId === 'string' && event.userId.length > 0 && typeof event.typing === 'boolean') return event
  } catch { /* Ignore malformed transient events. */ }
  return null
}

export function createTypingSender(send: (typing: boolean) => boolean) {
  let active = false, lastSent = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  function stop() {
    clearTimeout(timer)
    if (active) send(false)
    active = false
  }
  return {
    stop,
    input: (content: string) => {
      if (!content.trim()) { stop(); return }
      // Send immediately, then at most once every two seconds while editing.
      if (!active || Date.now() - lastSent >= 2000) {
        active = send(true)
        lastSent = Date.now()
      }
      clearTimeout(timer)
      timer = setTimeout(stop, 2000)
    },
  }
}
