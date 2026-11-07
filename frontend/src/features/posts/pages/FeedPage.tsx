import { useOutletContext } from 'react-router'
import { useAuth } from '../../auth/hooks/useAuth'
import type { useProfile } from '../../profile/hooks/useProfile'
import { PostSection } from '../components/PostSection'

export function FeedPage() {
  const { session } = useAuth()
  const { profile } = useOutletContext<ReturnType<typeof useProfile>>()
  return <main className="profile-content feed-page">
    <p className="feed-intro">Khám phá bài viết công khai và những câu chuyện bạn bè chia sẻ. Bài của bạn nằm ở Hồ sơ.</p>
    <PostSection key={session!.user.id} userId={session!.user.id}
      name={profile?.fullName || session!.user.fullName} avatarUrl={profile?.avatarUrl} feed />
  </main>
}
