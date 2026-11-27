import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

const KEY = 'enrollment.theme'

function stored(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

const systemQuery = () => window.matchMedia('(prefers-color-scheme: dark)')

function apply(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

/** Current theme plus a toggle. The choice is saved; until the user chooses, the operating system setting wins. */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const [theme, setTheme] = useState<Theme>(() => stored() ?? (systemQuery().matches ? 'dark' : 'light'))

  useEffect(() => apply(theme), [theme])

  useEffect(() => {
    const query = systemQuery()
    const onChange = (e: MediaQueryListEvent) => {
      if (!stored()) setTheme(e.matches ? 'dark' : 'light')
    }
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const toggle = useCallback(() => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    try {
      localStorage.setItem(KEY, next)
    } catch {
      /* the choice still applies for this visit */
    }
    setTheme(next)
  }, [theme])

  return { theme, toggle }
}
