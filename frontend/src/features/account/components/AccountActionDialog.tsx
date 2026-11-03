import { useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Field } from '../../../components/ui/Field'
import { Icon } from '../../../components/ui/Icon'
import { accountApi } from '../api'
import { useAccountForm } from '../hooks/useAccountForm'
import type { AccountAction } from '../types'
import { validateCurrentPassword } from '../utils'

export function AccountActionDialog({ action, onDismiss }: { action: AccountAction; onDismiss: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const form = useAccountForm()
  const [confirmed, setConfirmed] = useState(false)
  const navigate = useNavigate()
  const deleting = action === 'delete'
  const title = deleting ? 'Xóa tài khoản' : 'Vô hiệu hóa tài khoản'

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  return <dialog ref={dialog} className="account-action-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); if (!form.isBusy()) onDismiss() }}>
    <div className="action-dialog-heading"><span className={`settings-icon ${deleting ? 'danger-icon' : 'warning-icon'}`}><Icon name={deleting ? 'trash' : 'pause'} /></span><button type="button" className="icon-button" aria-label="Đóng hộp thoại" disabled={form.busy} onClick={onDismiss}><Icon name="close" /></button></div>
    <h2 id={titleId}>{title}?</h2>
    <div id={descriptionId} className="action-dialog-description">
      <p>{deleting ? 'Tài khoản sẽ bị đánh dấu đã xóa và không thể tiếp tục đăng nhập.' : 'Tài khoản sẽ ngừng hoạt động và bạn sẽ không thể đăng nhập.'}</p>
      <ul><li>Tất cả phiên đăng nhập trên các thiết bị sẽ bị thu hồi.</li><li>{deleting ? 'Đây là xóa mềm: dữ liệu vẫn được lưu trong hệ thống, chưa được xóa vĩnh viễn.' : 'Hiện chưa hỗ trợ tự kích hoạt lại tài khoản sau khi vô hiệu hóa.'}</li><li>{deleting ? 'Hiện chưa hỗ trợ tự khôi phục tài khoản đã xóa.' : 'Dữ liệu hồ sơ vẫn được giữ lại.'}</li></ul>
    </div>
    {form.error && <div className="feedback feedback-error" role="alert">{form.error}</div>}
    <form onSubmit={event => {
      event.preventDefault()
      if (!confirmed) return
      const values = new FormData(event.currentTarget)
      void form.run(async () => {
        const currentPassword = String(values.get('currentPassword') || '')
        validateCurrentPassword(currentPassword)
        await accountApi.changeStatus(action, { currentPassword })
        navigate('/login', { replace: true, state: { notice: deleting ? 'Tài khoản đã được xóa mềm. Bạn đã được đăng xuất.' : 'Tài khoản đã được vô hiệu hóa. Bạn đã được đăng xuất.' } })
      })
    }}>
      <fieldset disabled={form.busy}>
        <Field label="Mật khẩu hiện tại" name="currentPassword" type="password" placeholder="Nhập mật khẩu để xác nhận" autoComplete="current-password" required maxLength={72} error={form.fieldErrors.currentPassword} />
        <label className="action-confirmation"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} required /><span>Tôi đã hiểu tác động và muốn {deleting ? 'xóa' : 'vô hiệu hóa'} tài khoản.</span></label>
        <div className="action-dialog-footer"><button type="button" className="button button-secondary" onClick={onDismiss}>Giữ tài khoản</button><button type="submit" className={`button ${deleting ? 'button-danger' : 'button-warning'}`} disabled={!confirmed}>{form.busy ? 'Đang xử lý…' : title}</button></div>
      </fieldset>
    </form>
  </dialog>
}
