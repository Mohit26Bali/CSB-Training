import { useCallback, useEffect, useRef, useState } from 'react'

/** Returns value after it has stopped changing for delayMs, so typing does not fire work on every key. */
export function useDebounced<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

/**
 * One idempotency key per user action (see backend service/IdempotencyService).
 * Reuse the same key while retrying the same submit (double click, network error), and call renew() after the
 * action succeeded so the next, genuinely new action gets a fresh key.
 */
export function useIdempotencyKey(): { key: () => string; renew: () => void } {
  const ref = useRef<string>(crypto.randomUUID())
  const key = useCallback(() => ref.current, [])
  const renew = useCallback(() => {
    ref.current = crypto.randomUUID()
  }, [])
  return { key, renew }
}
