import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { Icon } from './Icon'

/**
 * App-wide notifications ("Deposit successful", server errors). Provided in main.tsx; pages call
 * useToast().success(...) / .error(...). Errors stay longer and are announced to screen readers as alerts.
 */
type ToastKind = 'success' | 'error'

interface ToastItem {
  id: number
  kind: ToastKind
  text: string
}

interface ToastApi {
  success: (text: string) => void
  error: (text: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), [])

  const push = useCallback(
    (kind: ToastKind, text: string) => {
      const id = nextId.current++
      setToasts((all) => [...all, { id, kind, text }])
      window.setTimeout(() => dismiss(id), kind === 'error' ? 6500 : 3800)
    },
    [dismiss],
  )

  const api = useMemo<ToastApi>(() => ({ success: (text) => push('success', text), error: (text) => push('error', text) }), [push])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex flex-col items-center gap-2 sm:left-auto sm:right-5 sm:items-end">
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.kind === 'error' ? 'alert' : 'status'}
            className={`pop-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border bg-surface px-4 py-3 text-sm shadow-xl ${
              t.kind === 'error' ? 'border-rose-500/40' : 'border-emerald-500/40'
            }`}
          >
            <span
              className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-full ${
                t.kind === 'error' ? 'bg-rose-500/15 text-rose-600 dark:text-rose-300' : 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300'
              }`}
            >
              <Icon name={t.kind === 'error' ? 'alert' : 'check'} className="size-3.5" />
            </span>
            <span className="flex-1 text-ink">{t.text}</span>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              aria-label="Dismiss notification"
              className="-mr-1 rounded p-0.5 text-faint hover:text-ink"
            >
              <Icon name="close" className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
