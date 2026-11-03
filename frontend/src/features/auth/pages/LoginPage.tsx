import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../../api/client'
import { Field } from '../../../components/ui/Field'
import { FormFeedback } from '../components/FormFeedback'
import { useAuthForm } from '../hooks/useAuthForm'
import { authStore } from '../session'

export function LoginPage() {
  const form = useAuthForm()
  const location = useLocation()
  const navigate = useNavigate()
  const [pendingEmail, setPendingEmail] = useState('')
  const state = location.state as { email?: string; notice?: string } | null
  return <>
    <div className="form-icon" aria-hidden="true">↗</div>
    <span className="eyebrow form-eyebrow">CHÀO MỪNG TRỞ LẠI</span>
    <h2>Chuyện hôm nay<br />của bạn thế nào?</h2>
    <p className="form-description">Đăng nhập để tiếp tục câu chuyện của bạn.</p>
    <FormFeedback error={form.error} notice={state?.notice} />
    <form onSubmit={form.submit(async values => {
      const email = String(values.get('email')).trim()
      setPendingEmail('')
      try {
        await authStore.login({ email, password: String(values.get('password')) })
        navigate('/', { replace: true })
      } catch (error) {
        if (error instanceof ApiError && error.problem?.detail === 'Email verification is required') setPendingEmail(email)
        throw error
      }
    })}>
      <fieldset disabled={form.busy}>
        <Field label="Email" name="email" type="email" placeholder="ban@example.com" autoComplete="email" required maxLength={255} defaultValue={state?.email} error={form.fieldErrors.email} />
        <Field label="Mật khẩu" name="password" type="password" placeholder="Nhập mật khẩu của bạn" autoComplete="current-password" required maxLength={72} error={form.fieldErrors.password} />
        <div className="form-link-row"><Link to="/forgot-password">Quên mật khẩu?</Link></div>
        <button className="button button-primary" type="submit">{form.busy ? 'Đang đăng nhập…' : 'Đăng nhập'}<span aria-hidden="true">→</span></button>
      </fieldset>
    </form>
    {pendingEmail && <Link className="verification-link" to="/verify-email" state={{ email: pendingEmail }}>Xác minh email của bạn →</Link>}
    <div className="form-divider"><span>Kết nối bắt đầu từ đây</span></div>
    <p className="form-bottom">Chưa có tài khoản? <Link to="/register">Tham gia ChatSocial</Link></p>
  </>
}
