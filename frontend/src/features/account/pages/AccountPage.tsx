import { useState } from 'react'
import { Icon } from '../../../components/ui/Icon'
import { useAuth } from '../../auth/hooks/useAuth'
import { AccountActionDialog } from '../components/AccountActionDialog'
import { ChangePasswordForm } from '../components/ChangePasswordForm'
import type { AccountAction } from '../types'
import '../account.css'

export function AccountPage() {
  const { session } = useAuth()
  const [action, setAction] = useState<AccountAction | null>(null)
  return <main className="settings-content">
    <div className="settings-page-heading"><span className="eyebrow form-eyebrow">AN TOÀN & QUYỀN KIỂM SOÁT</span><h1>Cài đặt tài khoản</h1><p>Quản lý bảo mật và những lựa chọn cho tài khoản của bạn.</p></div>
    <section className="settings-account-summary" aria-labelledby="account-info-title"><span className="settings-icon"><Icon name="user" /></span><div><h2 id="account-info-title">Thông tin tài khoản</h2><p>Email và tên người dùng đang dùng để nhận diện tài khoản.</p></div><dl><div><dt>Email</dt><dd>{session?.user.email}</dd></div><div><dt>Tên người dùng</dt><dd>@{session?.user.username}</dd></div></dl></section>
    <ChangePasswordForm />
    <section className="settings-card account-management" aria-labelledby="management-title"><div className="settings-card-heading"><span className="settings-icon"><Icon name="settings" /></span><div><h2 id="management-title">Quản lý tài khoản</h2><p>Hãy đọc kỹ tác động trước khi tiếp tục.</p></div></div>
      <div className="management-row"><div><h3>Vô hiệu hóa tài khoản</h3><p>Ngừng sử dụng tài khoản và đăng xuất trên mọi thiết bị. Hiện chưa hỗ trợ tự kích hoạt lại.</p></div><button className="button button-outline-warning" type="button" onClick={() => setAction('deactivate')}><Icon name="pause" />Vô hiệu hóa</button></div>
      <div className="management-row"><div><h3>Xóa tài khoản</h3><p>Tài khoản sẽ bị xóa mềm và không thể đăng nhập. Dữ liệu vẫn được giữ trong hệ thống.</p></div><button className="button button-outline-danger" type="button" onClick={() => setAction('delete')}><Icon name="trash" />Xóa tài khoản</button></div>
    </section>
    <footer className="settings-footer"><Icon name="shield" />Quyền kiểm soát tài khoản luôn ở trong tay bạn.</footer>
    {action && <AccountActionDialog action={action} onDismiss={() => setAction(null)} />}
  </main>
}
