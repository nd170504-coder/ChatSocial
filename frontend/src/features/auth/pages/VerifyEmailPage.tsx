import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { authApi } from '../api'
import { FormFeedback } from '../components/FormFeedback'
import { useAuthForm } from '../hooks/useAuthForm'
import { useCooldown } from '../hooks/useCooldown'

export function VerifyEmailPage() {
  const location = useLocation()
  const state = location.state as { email?: string; notice?: string; sent?: boolean } | null
  const [email, setEmail] = useState(state?.email || '')
  const [notice, setNotice] = useState(state?.notice || '')
  const form = useAuthForm()
  const cooldown = useCooldown(state?.sent ? 60 : 0)
  const navigate = useNavigate()
  return <>
    <Link className="back-link" to="/login">← Về đăng nhập</Link>
    <div className="form-icon" aria-hidden="true">✉</div>
    <h2>Kiểm tra<br />hộp thư của bạn.</h2>
    <p className="form-description">Nhập mã gồm 6 chữ số được gửi tới email của bạn. Nhớ kiểm tra cả thư rác nhé.</p>
    <FormFeedback error={form.error} notice={notice} />
    <form onSubmit={form.submit(async values => {
      await authApi.verifyEmail({ email: email.trim(), otp: String(values.get('otp')) })
      navigate('/login', { replace: true, state: { email: email.trim(), notice: 'Xác minh thành công! Bạn có thể đăng nhập.' } })
    })}>
      <fieldset disabled={form.busy}>
        <Field label="Email" name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} autoComplete="email" required maxLength={255} error={form.fieldErrors.email} />
        <Field className="otp-input" label="Mã xác minh" name="otp" placeholder="000000" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} minLength={6} autoComplete="one-time-code" required error={form.fieldErrors.otp} />
        <button className="button button-primary" type="submit">{form.busy ? 'Đang xử lý…' : 'Xác minh email'}<span aria-hidden="true">→</span></button>
        <button className="button button-secondary" type="submit" disabled={!!cooldown.seconds} formNoValidate
          onClick={event => {
            event.preventDefault()
            const emailInput = event.currentTarget.form?.elements.namedItem('email') as HTMLInputElement
            if (!emailInput.reportValidity()) return
            void form.run(async () => {
              await authApi.resendVerification(email.trim())
              cooldown.start()
              setNotice('Nếu tài khoản đang chờ xác minh và đủ điều kiện gửi, mã mới sẽ được gửi tới email của bạn.')
            })
          }}>{cooldown.seconds ? `Gửi lại sau ${cooldown.seconds}s` : 'Chưa nhận được mã? Gửi lại'}</button>
      </fieldset>
    </form>
  </>
}
