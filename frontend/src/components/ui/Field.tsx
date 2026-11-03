import { useId, useState } from 'react'
import type { InputHTMLAttributes } from 'react'

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

export function Field({ label, error, hint, type = 'text', ...props }: Props) {
  const id = useId()
  const [visible, setVisible] = useState(false)
  const password = type === 'password'
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="input-wrap">
        <input {...props} id={id} type={password && visible ? 'text' : type}
          aria-invalid={!!error} aria-describedby={error || hint ? `${id}-help` : undefined} />
        {password && <button type="button" className="password-toggle" aria-controls={id}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={visible}
          onClick={() => setVisible(!visible)}>{visible ? 'Ẩn' : 'Hiện'}</button>}
      </div>
      {(error || hint) && <small id={`${id}-help`} className={error ? 'field-error' : 'field-hint'}>{error || hint}</small>}
    </div>
  )
}
