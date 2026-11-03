import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../../../api/client'
import { Field } from '../../../components/ui/Field'
import { Icon } from '../../../components/ui/Icon'
import { profileApi } from '../api'
import type { Profile } from '../types'
import { buildProfileUpdate, genderLabels, latestBirthDate, profileError } from '../utils'

export function ProfileForm({ profile, onSaved }: { profile: Profile; onSaved: (profile: Profile) => void }) {
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [bio, setBio] = useState(profile.bio || '')
  const navigate = useNavigate()
  return <section className="profile-card edit-card" aria-labelledby="edit-title">
    <div className="profile-card-heading"><div><span className="eyebrow form-eyebrow">THỂ HIỆN CHÍNH MÌNH</span><h2 id="edit-title">Chỉnh sửa hồ sơ</h2><p>Một vài điều để mọi người hiểu bạn hơn.</p></div><span className="card-heading-icon"><Icon name="edit" /></span></div>
    {error && <div className="feedback feedback-error" role="alert">{error}</div>}
    <form onSubmit={async event => {
      event.preventDefault()
      if (locked.current) return
      const values = new FormData(event.currentTarget)
      locked.current = true
      setBusy(true)
      setError('')
      setFieldErrors({})
      try {
        const response = await profileApi.update(buildProfileUpdate(values))
        onSaved(response.data)
        navigate('/profile', { replace: true, state: { notice: 'Hồ sơ của bạn đã được cập nhật.' } })
      } catch (cause) {
        setError(profileError(cause))
        if (cause instanceof ApiError) setFieldErrors(cause.problem?.errors || {})
      } finally { locked.current = false; setBusy(false) }
    }}>
      <fieldset disabled={busy}>
        <Field label="Họ và tên" name="fullName" defaultValue={profile.fullName} required maxLength={100} autoComplete="name" error={fieldErrors.fullName} />
        <div className="field">
          <label htmlFor="profile-bio">Giới thiệu <span className="optional-label">(không bắt buộc)</span></label>
          <textarea id="profile-bio" name="bio" rows={4} maxLength={1000} value={bio} onChange={event => setBio(event.target.value)} placeholder="Bạn thích gì? Điều gì khiến bạn mỉm cười?" aria-invalid={!!fieldErrors.bio} aria-describedby="bio-help" />
          <div className="textarea-meta"><small id="bio-help" className={fieldErrors.bio ? 'field-error' : 'field-hint'}>{fieldErrors.bio || 'Một chút về bạn, theo cách của bạn.'}</small><span>{bio.length}/1.000</span></div>
        </div>
        <div className="field-grid">
          <Field label="Ngày sinh" name="dateOfBirth" type="date" defaultValue={profile.dateOfBirth || ''} max={latestBirthDate()} autoComplete="bday" hint="Không bắt buộc. Bạn cần đủ 15 tuổi." error={fieldErrors.dateOfBirth} />
          <div className="field"><label htmlFor="profile-gender">Giới tính</label><select id="profile-gender" name="gender" defaultValue={profile.gender || ''} aria-invalid={!!fieldErrors.gender} aria-describedby={fieldErrors.gender ? 'gender-error' : undefined}><option value="">Chưa chia sẻ</option>{Object.entries(genderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{fieldErrors.gender && <small id="gender-error" className="field-error">{fieldErrors.gender}</small>}</div>
        </div>
        <p className="edit-form-note">Để trống thông tin không bắt buộc nếu bạn muốn xóa thông tin đó khỏi hồ sơ.</p>
        <div className="profile-form-actions"><button className="button button-secondary" type="button" onClick={() => navigate('/profile')}>Hủy thay đổi</button><button className="button button-primary" type="submit"><Icon name="check" />{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div>
      </fieldset>
    </form>
  </section>
}
