import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

/**
 * Accessible dialog: closes on Escape and on a backdrop click, locks page scroll, moves focus into the dialog and returns
 * it afterwards. Used by components/TrailerModal (the video frame) and, as a narrow panel, by components/ConfirmDialog and
 * components/AdminUserForm.
 */
export function Modal({
  open,
  title,
  onClose,
  variant = 'video',
  children,
}: {
  open: boolean
  title: string
  onClose: () => void
  /** 'video' is the wide black player frame; 'form' is a narrow panel for forms and confirmations. */
  variant?: 'video' | 'form'
  children: ReactNode
}) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    panelRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      previouslyFocused?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-3 sm:p-6" role="presentation">
      <div className="fade-in absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`pop-in relative w-full overflow-hidden rounded-2xl shadow-2xl outline-none ${variant === 'form' ? 'max-w-lg bg-surface' : 'max-w-4xl bg-black'}`}
      >
        <div className="flex items-center justify-between gap-4 bg-surface px-4 py-3">
          <h2 id={titleId} className="truncate text-sm font-semibold text-ink">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="grid size-9 shrink-0 place-items-center rounded-full text-muted transition-colors hover:bg-ink/10 hover:text-ink"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
