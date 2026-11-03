import { Link, useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { authApi } from '../api'
import { FormFeedback } from '../components/FormFeedback'
import { useAuthForm } from '../hooks/useAuthForm'

export function ForgotPasswordPage() {
  const form = useAuthForm()
  const navigate = useNavigate()
  return <>
    <Link className="back-link" to="/login">← Về đăng nhập</Link>
    <div className="form-icon" aria-hidden="true">↺</div>
    <h2>Quên mật khẩu?<br />Không sao cả.</h2>
    <p className="form-description">Nhập email bạn đã đăng ký. Chúng mình sẽ gửi mã để bạn đặt lại mật khẩu.</p>
    <FormFeedback error={form.error} />
    <form onSubmit={form.submit(async values => {
      const email = String(values.get('email')).trim()
      await authApi.forgotPassword(email)
      navigate('/reset-password', { state: { email, sent: true } })
    })}>
      <fieldset disabled={form.busy}>
        <Field label="Email" name="email" type="email" placeholder="ban@example.com" autoComplete="email" required maxLength={255} error={form.fieldErrors.email} />
        <button className="button button-primary" type="submit">{form.busy ? 'Đang gửi…' : 'Gửi mã đặt lại mật khẩu'}<span aria-hidden="true">→</span></button>
      </fieldset>
    </form>
    <p className="form-bottom">Đã có mã? <Link to="/reset-password">Đặt lại mật khẩu</Link></p>
  </>
}
