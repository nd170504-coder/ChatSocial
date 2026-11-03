import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { authApi } from '../api'
import { validatePassword } from '../errors'
import { FormFeedback } from '../components/FormFeedback'
import { useAuthForm } from '../hooks/useAuthForm'
import { useCooldown } from '../hooks/useCooldown'

export function ResetPasswordPage() {
  const location = useLocation()
  const state = location.state as { email?: string; sent?: boolean } | null
  const [email, setEmail] = useState(state?.email || '')
  const [notice, setNotice] = useState(state?.sent ? 'Nếu tài khoản đủ điều kiện, mã đặt lại mật khẩu sẽ được gửi tới email của bạn.' : '')
  const form = useAuthForm()
  const cooldown = useCooldown(state?.sent ? 60 : 0)
  const navigate = useNavigate()
  return <>
    <Link className="back-link" to="/login">← Về đăng nhập</Link>
    <h2>Mật khẩu mới.<br />Khởi đầu mới.</h2>
    <p className="form-description">Dùng mã trong email và chọn một mật khẩu mới cho tài khoản của bạn.</p>
    <FormFeedback error={form.error} notice={notice} />
    <form onSubmit={form.submit(async values => {
      const newPassword = String(values.get('newPassword'))
      const confirmPassword = String(values.get('confirmPassword'))
      validatePassword(newPassword, confirmPassword)
      await authApi.resetPassword({ email: email.trim(), otp: String(values.get('otp')), newPassword, confirmPassword })
      navigate('/login', { replace: true, state: { email: email.trim(), notice: 'Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.' } })
    })}>
      <fieldset disabled={form.busy}>
        <Field label="Email" name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} autoComplete="email" required maxLength={255} error={form.fieldErrors.email} />
        <Field className="otp-input" label="Mã xác minh" name="otp" placeholder="000000" inputMode="numeric" pattern="[0-9]{6}" minLength={6} maxLength={6} autoComplete="one-time-code" required error={form.fieldErrors.otp} />
        <Field label="Mật khẩu mới" name="newPassword" type="password" placeholder="Ít nhất 8 ký tự" autoComplete="new-password" required minLength={8} maxLength={72} error={form.fieldErrors.newPassword} />
        <Field label="Xác nhận mật khẩu mới" name="confirmPassword" type="password" placeholder="Nhập lại mật khẩu mới" autoComplete="new-password" required minLength={8} maxLength={72} error={form.fieldErrors.confirmPassword} />
        <button className="button button-primary" type="submit">{form.busy ? 'Đang xử lý…' : 'Đặt lại mật khẩu'}<span aria-hidden="true">→</span></button>
        <button className="button button-secondary" type="submit" disabled={!!cooldown.seconds} formNoValidate onClick={event => {
          event.preventDefault()
          const emailInput = event.currentTarget.form?.elements.namedItem('email') as HTMLInputElement
          if (!emailInput.reportValidity()) return
          void form.run(async () => {
            await authApi.forgotPassword(email.trim())
            cooldown.start()
            setNotice('Nếu tài khoản đủ điều kiện gửi, mã mới sẽ được gửi tới email của bạn.')
          })
        }}>{cooldown.seconds ? `Gửi lại sau ${cooldown.seconds}s` : 'Gửi lại mã xác minh'}</button>
      </fieldset>
    </form>
  </>
}
