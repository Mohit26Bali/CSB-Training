import type { ReactNode } from 'react'
import { Button } from './ui'
import { Modal } from './Modal'

/**
 * A small "are you sure?" dialog (on top of components/Modal) used before something permanent: deleting an account
 * (pages/ProfilePage, pages/AdminUsersPage). {@code children} holds the explanation and, when needed, extra inputs such as the
 * password; {@code error} shows why the last attempt failed without closing the dialog; {@code busy} keeps the button from
 * being pressed twice.
 */
export function ConfirmDialog({
  open,
  title,
  confirmLabel,
  busy = false,
  error,
  onConfirm,
  onCancel,
  children,
}: {
  open: boolean
  title: string
  confirmLabel: string
  busy?: boolean
  error?: string | null
  onConfirm: () => void
  onCancel: () => void
  children: ReactNode
}) {
  return (
    <Modal open={open} title={title} onClose={onCancel} variant="form">
      <form
        className="space-y-4 bg-surface p-5"
        onSubmit={(e) => {
          e.preventDefault()
          onConfirm()
        }}
      >
        {children}
        {error && (
          <p role="alert" className="rounded-xl border border-ember-500/40 bg-ember-500/10 px-4 py-3 text-sm text-ink">
            {error}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-3">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" loading={busy}>
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
