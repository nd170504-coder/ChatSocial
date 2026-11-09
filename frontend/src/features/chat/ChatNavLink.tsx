import { NavLink } from 'react-router'
import { Icon } from '../../components/ui/Icon'
import { useChat } from './ChatProvider'

export function ChatNavLink() {
  const { hasNewMessages } = useChat()
  return <NavLink className="sidebar-link" to="/messages"><Icon name="comment" />Tin nhắn{hasNewMessages && <span className="chat-nav-dot" aria-label="Có tin nhắn mới" />}</NavLink>
}
