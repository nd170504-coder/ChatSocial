import { useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { Icon } from '../../../components/ui/Icon'
import { accountApi } from '../api'
import { useAccountForm } from '../hooks/useAccountForm'
import { buildPasswordChange } from '../utils'

export function ChangePasswordForm() {
  const form = useAccountForm()
  const navigate = useNavigate()
  return <section className="settings-card" id="security" aria-labelledby="password-title">
    <div className="settings-card-heading"><span className="settings-icon"><Icon name="lock" /></span><div><h2 id="password-title">Đổi mật khẩu</h2><p>Giữ tài khoản an toàn bằng một mật khẩu riêng cho bạn.</p></div></div>
    <div className="settings-info"><Icon name="shield" /><p>Sau khi đổi mật khẩu, tất cả thiết bị sẽ được đăng xuất. Bạn cần đăng nhập lại bằng mật khẩu mới.</p></div>
    {form.error && <div className="feedback feedback-error" role="alert">{form.error}</div>}
    <form onSubmit={event => {
      event.preventDefault()
      const values = new FormData(event.currentTarget)
      void form.run(async () => {
        await accountApi.changePassword(buildPasswordChange(values))
        navigate('/login', { replace: true, state: { notice: 'Đổi mật khẩu thành công. Hãy đăng nhập lại bằng mật khẩu mới.' } })
      })
    }}>
      <fieldset disabled={form.busy}>
        <Field label="Mật khẩu hiện tại" name="currentPassword" type="password" placeholder="Nhập mật khẩu hiện tại" autoComplete="current-password" required maxLength={72} error={form.fieldErrors.currentPassword} />
        <div className="field-grid">
          <Field label="Mật khẩu mới" name="newPassword" type="password" placeholder="Ít nhất 8 ký tự" autoComplete="new-password" required minLength={8} maxLength={72} error={form.fieldErrors.newPassword} />
          <Field label="Xác nhận mật khẩu mới" name="confirmPassword" type="password" placeholder="Nhập lại mật khẩu mới" autoComplete="new-password" required minLength={8} maxLength={72} error={form.fieldErrors.confirmPassword} />
        </div>
        <div className="settings-form-footer"><span>Ít nhất 8 ký tự, tối đa 72 byte.</span><button className="button button-primary" type="submit"><Icon name="lock" />{form.busy ? 'Đang cập nhật…' : 'Cập nhật mật khẩu'}</button></div>
      </fieldset>
    </form>
  </section>
}
