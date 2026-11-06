import { useOutletContext } from 'react-router'
import { useAuth } from '../../auth/hooks/useAuth'
import type { useProfile } from '../../profile/hooks/useProfile'
import { PostSection } from '../components/PostSection'

export function PublicPostsPage() {
  const { session } = useAuth()
  const { profile } = useOutletContext<ReturnType<typeof useProfile>>()
  return <main className="profile-content public-posts-page">
    <p className="public-posts-intro">Một góc để khám phá những câu chuyện, khoảnh khắc và chia sẻ từ mọi người.</p>
    <PostSection key={session!.user.id} userId={session!.user.id}
      name={profile?.fullName || session!.user.fullName} avatarUrl={profile?.avatarUrl} publicFeed />
  </main>
}
