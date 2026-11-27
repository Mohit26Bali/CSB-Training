import { useSyncExternalStore } from 'react'

const QUERY = '(prefers-reduced-motion: reduce)'

function subscribe(onChange) {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {}
  const mql = window.matchMedia(QUERY)
  mql.addEventListener('change', onChange)
  return () => mql.removeEventListener('change', onChange)
}

const getSnapshot = () => typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia(QUERY).matches

/** True when the operating system asks for reduced motion. Updates live if the setting changes. */
export function usePrefersReducedMotion() {
  return useSyncExternalStore(subscribe, getSnapshot, () => false)
}
