import { useTheme } from '../theme'
import { Icon } from './Icon'

/** Sun / moon button; state and persistence live in src/theme.ts. Placed in the app header and on the auth pages. */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      className={`grid size-10 place-items-center rounded-xl border border-line bg-surface text-muted transition-colors hover:bg-surface-2 hover:text-ink ${className}`}
    >
      <Icon name={dark ? 'sun' : 'moon'} className="size-5" />
    </button>
  )
}
