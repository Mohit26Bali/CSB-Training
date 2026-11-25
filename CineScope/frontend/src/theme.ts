import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

// Same key as the inline script in index.html, which applies the saved choice before the first paint.
const KEY = 'cinescope.theme'

function stored(): Theme {
  try {
    return localStorage.getItem(KEY) === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

/** Current theme plus a toggle (used by components/ThemeToggle). Dark is the default; the choice is remembered. */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const [theme, setTheme] = useState<Theme>(stored)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

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
