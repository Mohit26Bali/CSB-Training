import { useState, type InputHTMLAttributes } from 'react'
import { Icon } from './Icon'
import { Field } from './ui'

/** A password input with a show / hide button. Used by pages/auth/LoginPage and RegisterPage. */
export function PasswordField({
  label,
  error,
  hint,
  ...rest
}: { label: string; error?: string; hint?: string } & Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [visible, setVisible] = useState(false)
  return (
    <Field
      label={label}
      error={error}
      hint={hint}
      type={visible ? 'text' : 'password'}
      suffix={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          className="grid size-8 place-items-center rounded-lg text-faint transition-colors hover:text-ink"
        >
          <Icon name={visible ? 'eyeOff' : 'eye'} className="size-5" />
        </button>
      }
      {...rest}
    />
  )
}
