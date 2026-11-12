import { Link } from 'react-router'
import { Icon } from '../../../components/ui/Icon'
import type { Profile } from '../types'
import { formatBirthDate, genderLabels } from '../utils'

export function ProfileAbout({ profile, email }: {
  profile: Pick<Profile, 'bio' | 'dateOfBirth' | 'gender'>; email?: string
}) {
  return <div id="about" className="profile-details-grid">
    <section className="profile-card bio-card"><div className="profile-card-heading"><h2>Giới thiệu</h2><span className="card-heading-icon"><Icon name="user" /></span></div>
      {profile.bio ? <p className="profile-bio">{profile.bio}</p> : <div className="profile-empty"><p>Chưa thêm lời giới thiệu.</p>{email && <Link to="/profile/edit">Thêm lời giới thiệu →</Link>}</div>}
    </section>
    <section className="profile-card info-card"><div className="profile-card-heading"><h2>Thông tin cá nhân</h2><span className="card-heading-icon"><Icon name="user" /></span></div>
      <dl className="profile-info">
        {email && <div><dt>Email <span className="profile-private-label">Chỉ mình tôi</span></dt><dd>{email}</dd></div>}
        <div><dt>Ngày sinh</dt><dd className={!profile.dateOfBirth ? 'empty-value' : ''}>{profile.dateOfBirth ? formatBirthDate(profile.dateOfBirth) : 'Chưa chia sẻ'}</dd></div>
        <div><dt>Giới tính</dt><dd className={!profile.gender ? 'empty-value' : ''}>{profile.gender ? genderLabels[profile.gender] : 'Chưa chia sẻ'}</dd></div>
      </dl>
    </section>
  </div>
}
