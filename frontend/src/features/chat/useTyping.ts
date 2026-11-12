import { useCallback, useEffect, useRef, useState } from 'react'
import { useChat } from './ChatProvider'
import { createTypingSender } from './typing'

export function useTyping(conversationId: string, otherUserId: string | undefined, enabled: boolean) {
  const { status, connectionRevision, subscribeTyping, subscribe, sendTyping } = useChat()
  const [incoming, setIncoming] = useState(false)
  const sender = useRef<ReturnType<typeof createTypingSender> | null>(null)

  useEffect(() => {
    setIncoming(false)
    if (!enabled || !otherUserId || status !== 'connected') return
    const current = createTypingSender(value => sendTyping(conversationId, value))
    sender.current = current
    let expiry: ReturnType<typeof setTimeout> | undefined
    const clear = () => { clearTimeout(expiry); setIncoming(false) }
    const unsubscribe = subscribeTyping(event => {
      if (event.conversationId !== conversationId || event.userId !== otherUserId) return
      clearTimeout(expiry)
      setIncoming(event.typing)
      // A lost stop event must never leave the indicator visible forever.
      if (event.typing) expiry = setTimeout(clear, 5000)
    })
    const unsubscribeChat = subscribe(event => {
      if (event.conversationId === conversationId && event.type === 'MESSAGE_CREATED' && event.message.senderId === otherUserId) clear()
    })
    const hidden = () => { if (document.visibilityState !== 'visible') { current.stop(); clear() } }
    const blur = () => current.stop()
    document.addEventListener('visibilitychange', hidden)
    window.addEventListener('blur', blur)
    return () => {
      current.stop(); sender.current = null; clearTimeout(expiry)
      unsubscribe(); unsubscribeChat()
      document.removeEventListener('visibilitychange', hidden)
      window.removeEventListener('blur', blur)
    }
  }, [conversationId, otherUserId, enabled, status, connectionRevision, subscribeTyping, subscribe, sendTyping])

  const input = useCallback((content: string) => { if (document.visibilityState === 'visible') sender.current?.input(content) }, [])
  const stop = useCallback(() => sender.current?.stop(), [])
  return { incoming: enabled && status === 'connected' && incoming, input, stop }
}
