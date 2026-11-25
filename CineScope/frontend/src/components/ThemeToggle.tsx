import { useTheme } from '../theme'
import { Icon } from './Icon'

/** Sun / moon button in the header; the state lives in src/theme.ts. */
export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      className="grid size-10 place-items-center rounded-full text-muted transition-colors hover:bg-ink/10 hover:text-ink"
    >
      <Icon name={dark ? 'sun' : 'moon'} className="size-5" />
    </button>
  )
}
