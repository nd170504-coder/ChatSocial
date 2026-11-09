import { Link, useParams } from 'react-router'
import { Icon } from '../../components/ui/Icon'
import { ConversationList } from './ConversationList'
import { ChatThread } from './ChatThread'
import './chat.css'

export function ChatPage() {
  const { conversationId } = useParams()
  return <main className={`chat-page${conversationId ? ' has-thread' : ''}`}>
    <ConversationList selected={conversationId} />
    {conversationId ? <ChatThread key={conversationId} conversationId={conversationId} /> : <section className="chat-welcome"><Icon name="comment" width="52" height="52" /><h2>Câu chuyện bắt đầu từ một lời chào</h2><p>Chọn một cuộc trò chuyện hoặc mở hồ sơ bạn bè để nhắn tin.</p><Link to="/friends">Tìm trong danh sách bạn bè →</Link></section>}
  </main>
}
