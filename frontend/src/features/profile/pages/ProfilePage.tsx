import { useState } from 'react'
import { Link, useLocation, useOutletContext } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { Avatar } from '../components/Avatar'
import { ImageUploadDialog } from '../components/ImageUploadDialog'
import { ProfileForm } from '../components/ProfileForm'
import type { useProfile } from '../hooks/useProfile'
import type { ProfileImageType } from '../types'
import { formatBirthDate, genderLabels } from '../utils'
import '../profile.css'

export function ProfilePage({ editing = false }: { editing?: boolean }) {
  const { session } = useAuth()
  const { profile, loading, error, retry, updateProfile } = useOutletContext<ReturnType<typeof useProfile>>()
  const [uploadType, setUploadType] = useState<ProfileImageType | null>(null)
  const [uploadNotice, setUploadNotice] = useState<{ key: string; text: string } | null>(null)
  const [failedCover, setFailedCover] = useState<string | null>(null)
  const location = useLocation()
  const routeNotice = (location.state as { notice?: string } | null)?.notice
  const notice = uploadNotice?.key === location.key ? uploadNotice.text : ''

  if (loading) return <main className="profile-content"><div className="profile-page-heading"><span className="eyebrow form-eyebrow">KHÔNG GIAN CÁ NHÂN</span><h1>Hồ sơ của tôi</h1></div><div className="profile-loading" role="status"><div className="skeleton skeleton-cover" /><div className="skeleton skeleton-line" /><div className="skeleton skeleton-line short" /><p>Đang tải hồ sơ của bạn…</p></div></main>
  if (error || !profile) return <main className="profile-content"><section className="profile-card profile-load-error" role="alert"><Icon name="user" width="36" height="36" /><h1>Chưa thể tải hồ sơ</h1><p>{error || 'Vui lòng thử lại.'}</p><button type="button" className="button button-primary compact-button" onClick={retry}>Thử lại</button></section></main>

  return <main className="profile-content">
    <div className="profile-page-heading"><div><span className="eyebrow form-eyebrow">KHÔNG GIAN CÁ NHÂN</span><h1>{editing ? 'Chỉnh sửa hồ sơ' : 'Hồ sơ của tôi'}</h1><p>{editing ? 'Cập nhật một chút, để hồ sơ thêm giống bạn.' : 'Một góc nhỏ để kể câu chuyện của bạn.'}</p></div>{editing && <Link className="profile-back-link" to="/profile"><Icon name="arrow" /> Về hồ sơ</Link>}</div>
    {(notice || (!editing && routeNotice)) && <div className="feedback feedback-success" role="status">{notice || routeNotice}</div>}
    <section className="profile-hero" aria-label="Ảnh và thông tin hồ sơ">
      <div className="profile-cover">
        {profile.coverUrl && failedCover !== profile.coverUrl
          ? <img src={profile.coverUrl} alt={`Ảnh bìa của ${profile.fullName}`} onError={() => setFailedCover(profile.coverUrl)} />
          : <div className="default-cover" aria-hidden="true"><span className="cover-orbit orbit-one" /><span className="cover-orbit orbit-two" /><span className="cover-caption">YOUR STORY, YOUR SPACE.</span><span className="cover-star">✳</span></div>}
        <button type="button" className="cover-change-button" onClick={() => setUploadType('cover')}><Icon name="camera" />Thay ảnh bìa</button>
      </div>
      <div className="profile-identity">
        <div className="avatar-editor"><Avatar name={profile.fullName} src={profile.avatarUrl} /><button type="button" className="avatar-change-button" aria-label="Thay ảnh đại diện" onClick={() => setUploadType('avatar')}><Icon name="camera" /></button></div>
        <div className="identity-text"><h2>{profile.fullName}</h2><p>@{session!.user.username}</p></div>
        {!editing && <Link className="button button-secondary profile-edit-button" to="/profile/edit"><Icon name="edit" />Chỉnh sửa hồ sơ</Link>}
      </div>
      <div className="profile-tab"><Icon name="user" />Giới thiệu</div>
    </section>
    {editing ? <ProfileForm key={profile.userId} profile={profile} onSaved={updateProfile} /> : <div className="profile-details-grid">
      <section className="profile-card bio-card"><div className="profile-card-heading"><h2>Đôi điều về tôi</h2><span className="card-heading-icon"><Icon name="user" /></span></div>{profile.bio ? <p className="profile-bio">{profile.bio}</p> : <div className="profile-empty"><span aria-hidden="true">“</span><p>Mỗi người đều có một câu chuyện.<br />Câu chuyện của bạn là gì?</p><Link to="/profile/edit">Thêm lời giới thiệu →</Link></div>}</section>
      <section className="profile-card info-card"><div className="profile-card-heading"><h2>Thông tin cá nhân</h2><span className="card-heading-icon"><Icon name="mail" /></span></div><dl className="profile-info"><div><dt>Email</dt><dd>{session!.user.email}</dd></div><div><dt>Ngày sinh</dt><dd className={!profile.dateOfBirth ? 'empty-value' : ''}>{profile.dateOfBirth ? formatBirthDate(profile.dateOfBirth) : 'Chưa chia sẻ'}</dd></div><div><dt>Giới tính</dt><dd className={!profile.gender ? 'empty-value' : ''}>{profile.gender ? genderLabels[profile.gender] : 'Chưa chia sẻ'}</dd></div></dl></section>
    </div>}
    <footer className="profile-footer">Một hồ sơ mang dấu ấn của bạn.<span>ChatSocial</span></footer>
    {uploadType && <ImageUploadDialog type={uploadType} onDismiss={() => setUploadType(null)} onSaved={updated => {
      updateProfile(updated)
      setUploadNotice({ key: location.key, text: uploadType === 'avatar' ? 'Ảnh đại diện đã được cập nhật.' : 'Ảnh bìa đã được cập nhật.' })
      setUploadType(null)
    }} />}
  </main>
}
