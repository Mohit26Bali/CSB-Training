import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from './api'

export interface AsyncState<T> {
  data: T | null
  error: string | null
  loading: boolean
  reload: () => void
}

/** Runs `load` when the dependencies change and keeps only the newest answer (a slow old request cannot overwrite a newer one). */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [state, setState] = useState<{ data: T | null; error: string | null; loading: boolean }>({ data: null, error: null, loading: true })
  const ticket = useRef(0)
  const [nonce, setNonce] = useState(0)
  const reload = useCallback(() => setNonce((n) => n + 1), [])

  useEffect(() => {
    const mine = ++ticket.current
    setState((s) => ({ ...s, loading: true, error: null }))
    load()
      .then((data) => {
        if (mine === ticket.current) setState({ data, error: null, loading: false })
      })
      .catch((err: unknown) => {
        if (mine === ticket.current) setState({ data: null, error: err instanceof ApiError ? err.message : 'Something went wrong.', loading: false })
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { ...state, reload }
}

export function messageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Something went wrong.'
}
