import { Link, Navigate, Route, Routes } from 'react-router'
import { AuthLayout } from '../layouts/AuthLayout'
import { AuthGate } from '../features/auth/components/AuthGate'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { VerifyEmailPage } from '../features/auth/pages/VerifyEmailPage'
import { ForgotPasswordPage } from '../features/auth/pages/ForgotPasswordPage'
import { ResetPasswordPage } from '../features/auth/pages/ResetPasswordPage'
import { AppLayout } from '../layouts/AppLayout'
import { ProfilePage } from '../features/profile/pages/ProfilePage'
import { AccountPage } from '../features/account/pages/AccountPage'

export function AppRouter() {
  return <Routes>
    <Route element={<AuthGate />}>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>
    </Route>
    <Route element={<AuthGate requireSession />}>
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/profile" replace />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/profile/edit" element={<ProfilePage editing />} />
        <Route path="/settings/account" element={<AccountPage />} />
      </Route>
    </Route>
    <Route path="*" element={<main className="session-state"><h1>Không tìm thấy trang</h1><p>Đường dẫn này không tồn tại.</p><Link className="button button-primary" to="/">Về trang chính</Link></main>} />
  </Routes>
}
