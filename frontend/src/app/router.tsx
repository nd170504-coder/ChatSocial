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
import { PostPage } from '../features/posts/pages/PostPage'
import { FeedPage } from '../features/posts/pages/FeedPage'
import { FindFriendsPage } from '../features/friends/pages/FindFriendsPage'
import { UserProfilePage } from '../features/profile/pages/UserProfilePage'
import { FriendRequestsPage } from '../features/friends/pages/FriendRequestsPage'
import { FriendsPage } from '../features/friends/pages/FriendsPage'

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
        <Route path="/" element={<Navigate to="/feed" replace />} />
        <Route path="/feed" element={<FeedPage />} />
        <Route path="/friends/search" element={<FindFriendsPage />} />
        <Route path="/friends" element={<FriendsPage />} />
        <Route path="/friends/requests" element={<FriendRequestsPage />} />
        <Route path="/users/:userId" element={<UserProfilePage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/profile/edit" element={<ProfilePage editing />} />
        <Route path="/posts/:postId" element={<PostPage />} />
        <Route path="/settings/account" element={<AccountPage />} />
      </Route>
    </Route>
    <Route path="*" element={<main className="session-state"><h1>Không tìm thấy trang</h1><p>Đường dẫn này không tồn tại.</p><Link className="button button-primary" to="/">Về trang chính</Link></main>} />
  </Routes>
}
