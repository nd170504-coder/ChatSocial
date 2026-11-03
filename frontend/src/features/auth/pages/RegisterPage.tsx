import { Link, useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { authApi } from '../api'
import { validatePassword } from '../errors'
import { FormFeedback } from '../components/FormFeedback'
import { useAuthForm } from '../hooks/useAuthForm'

export function RegisterPage() {
  const form = useAuthForm()
  const navigate = useNavigate()
  return <>
    <span className="eyebrow form-eyebrow">MỘT KHỞI ĐẦU MỚI</span>
    <h2>Câu chuyện mới.<br />Kết nối mới.</h2>
    <p className="form-description">Tạo tài khoản và tìm thấy không gian của bạn.</p>
    <FormFeedback error={form.error} />
    <form onSubmit={form.submit(async values => {
      const password = String(values.get('password'))
      validatePassword(password, String(values.get('confirmPassword')))
      const email = String(values.get('email')).trim()
      await authApi.register({ email, password, username: String(values.get('username')).trim().toLowerCase(), fullName: String(values.get('fullName')).trim() })
      navigate('/verify-email', { state: { email, sent: true, notice: 'Tài khoản đã được tạo. Kiểm tra email để lấy mã xác minh.' } })
    })}>
      <fieldset disabled={form.busy}>
        <div className="field-grid">
          <Field label="Họ và tên" name="fullName" placeholder="Nguyễn Minh Anh" autoComplete="name" required maxLength={100} error={form.fieldErrors.fullName} />
          <Field label="Tên người dùng" name="username" placeholder="minhanh" autoComplete="username" required pattern="[a-zA-Z0-9_]{3,50}" minLength={3} maxLength={50} hint="3–50 chữ cái, số hoặc dấu _" error={form.fieldErrors.username} />
        </div>
        <Field label="Email" name="email" type="email" placeholder="ban@example.com" autoComplete="email" required maxLength={255} error={form.fieldErrors.email} />
        <Field label="Mật khẩu" name="password" type="password" placeholder="Ít nhất 8 ký tự" autoComplete="new-password" required minLength={8} maxLength={72} error={form.fieldErrors.password} />
        <Field label="Xác nhận mật khẩu" name="confirmPassword" type="password" placeholder="Nhập lại mật khẩu" autoComplete="new-password" required minLength={8} maxLength={72} />
        <button className="button button-primary" type="submit">{form.busy ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}<span aria-hidden="true">→</span></button>
      </fieldset>
    </form>
    <p className="form-bottom">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
  </>
}
