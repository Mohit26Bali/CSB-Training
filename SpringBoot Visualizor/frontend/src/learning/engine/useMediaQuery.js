import { useSyncExternalStore } from 'react'

/** Tracks a CSS media query (for example the desktop breakpoint) and re-renders when it changes. */
export function useMediaQuery(query) {
  const subscribe = (onChange) => {
    if (typeof window === 'undefined' || !window.matchMedia) return () => {}
    const mql = window.matchMedia(query)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }
  const getSnapshot = () => typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia(query).matches
  return useSyncExternalStore(subscribe, getSnapshot, () => false)
}
