import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

/**
 * Accessible dialog: closes on Escape and on a backdrop click, locks page scroll, moves focus into the dialog and
 * returns it to the previous element on close. Used by the money forms (pages/MoneyModal), account opening
 * (pages/OpenAccountModal) and the admin screens.
 */
export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  wide = false,
}: {
  open: boolean
  title: string
  description?: string
  onClose: () => void
  children: ReactNode
  wide?: boolean
}) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const scrollbar = window.innerWidth - document.documentElement.clientWidth
    document.body.style.overflow = 'hidden'
    document.body.style.paddingRight = `${scrollbar}px`
    const firstField = panelRef.current?.querySelector<HTMLElement>('input, select, textarea, button:not([data-close])')
    ;(firstField ?? panelRef.current)?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      document.body.style.paddingRight = ''
      previouslyFocused?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4" role="presentation">
      <div className="fade-in absolute inset-0 bg-[#050b1a]/60 backdrop-blur-sm" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`pop-in relative max-h-[92vh] w-full overflow-y-auto rounded-t-3xl border border-line bg-surface p-6 shadow-2xl outline-none sm:rounded-3xl ${
          wide ? 'sm:max-w-2xl' : 'sm:max-w-md'
        }`}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 id={titleId} className="text-lg font-semibold tracking-tight text-ink">
              {title}
            </h2>
            {description && <p className="mt-1 text-sm text-muted">{description}</p>}
          </div>
          <button
            type="button"
            data-close
            onClick={onClose}
            aria-label="Close dialog"
            className="-mr-2 -mt-1 grid size-9 shrink-0 place-items-center rounded-xl text-muted transition-colors hover:bg-brand-500/10 hover:text-ink"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
