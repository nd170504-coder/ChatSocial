import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { ApiError } from '../../../api/client'
import { useAuth } from '../../auth/hooks/useAuth'
import { PostSection } from '../../posts/components/PostSection'
import { FriendRequestAction } from '../../friends/components/FriendRequestAction'
import { profileApi } from '../api'
import { Avatar } from '../components/Avatar'
import type { UserProfile } from '../types'
import '../profile.css'

export function UserProfilePage() {
  const { userId = '' } = useParams()
  const { session } = useAuth()
  if (userId === session?.user.id) return <Navigate to="/profile" replace />
  return <UserProfileView key={userId} userId={userId} />
}

function UserProfileView({ userId }: { userId: string }) {
  const [revision, setRevision] = useState(0)
  return <UserProfileContent key={revision} userId={userId} onRefresh={() => setRevision(value => value + 1)} />
}

function UserProfileContent({ userId, onRefresh }: { userId: string; onRefresh: () => void }) {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [failedCover, setFailedCover] = useState(false)
  const [sendingRequest, setSendingRequest] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void profileApi.getUser(userId, controller.signal).then(response => {
      if (active) setProfile(response.data)
    }).catch(cause => {
      if (active) setError(cause instanceof ApiError && cause.status === 404
        ? 'Hồ sơ không tồn tại hoặc tài khoản không còn hoạt động.'
        : 'Chưa thể tải hồ sơ. Vui lòng kiểm tra kết nối và thử lại.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; controller.abort() }
  }, [userId])

  if (loading) return <main className="profile-content"><div className="profile-loading" role="status"><div className="skeleton skeleton-cover" /><div className="skeleton skeleton-line" /><p>Đang tải hồ sơ…</p></div></main>
  if (error || !profile) return <main className="profile-content"><section className="profile-card profile-load-error" role="alert"><h1>Chưa thể xem hồ sơ</h1><p>{error}</p><button className="button button-primary compact-button" type="button" onClick={onRefresh}>Thử lại</button><Link className="visitor-back-link" to="/friends/search">Về tìm bạn</Link></section></main>

  return <main className="profile-content visitor-profile">
    <div className="profile-page-heading"><div><span className="eyebrow form-eyebrow">MỖI NGƯỜI, MỘT CÂU CHUYỆN</span><h1>Hồ sơ cá nhân</h1></div><button className="visitor-refresh" type="button" disabled={sendingRequest} onClick={onRefresh}>Làm mới</button></div>
    <section className="profile-hero" aria-label={`Hồ sơ của ${profile.fullName}`}>
      <div className="profile-cover">{profile.coverUrl && !failedCover
        ? <img src={profile.coverUrl} alt={`Ảnh bìa của ${profile.fullName}`} onError={() => setFailedCover(true)} />
        : <div className="default-cover" aria-hidden="true"><span className="cover-orbit orbit-one" /><span className="cover-orbit orbit-two" /><span className="cover-caption">YOUR STORY, YOUR SPACE.</span><span className="cover-star">✳</span></div>}</div>
      <div className="profile-identity"><div className="avatar-editor"><Avatar name={profile.fullName} src={profile.avatarUrl} /></div><div className="identity-text"><h2>{profile.fullName}</h2><p>@{profile.username}</p></div><FriendRequestAction profile={profile} onUpdated={setProfile} onBusyChange={setSendingRequest} /></div>
      <nav className="profile-section-nav" aria-label="Nội dung hồ sơ"><a href="#posts">Bài viết</a><a href="#about">Giới thiệu</a></nav>
    </section>
    <section id="about" className="profile-card"><div className="profile-card-heading"><h2>Giới thiệu</h2></div><p className="profile-bio">{profile.bio || 'Người dùng chưa thêm lời giới thiệu.'}</p></section>
    <p className="visitor-visibility-note">{profile.relationship === 'FRIENDS' ? 'Bạn có thể xem bài công khai và bài dành cho bạn bè.' : 'Bạn đang xem các bài viết công khai.'}</p>
    <PostSection userId={profile.userId} name={profile.fullName} avatarUrl={profile.avatarUrl} readOnly />
  </main>
}
