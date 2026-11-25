import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/endpoints'
import type { AuthResponse } from '../api/types'
import { AuthContext, type AuthValue } from './authState'
import { SESSION_EXPIRED_EVENT, clearSession, loadSession, saveSession, toSession, type Session } from '../lib/session'

/**
 * Who is signed in, for the whole app. Wrapped around the routes in main.tsx; components read it through
 * auth/useAuth.ts. The session itself lives in lib/session.ts (sessionStorage) so api/client.ts can attach the token;
 * this provider mirrors it into React state so the header, the guarded pages and the watchlist controls re-render on
 * sign-in / sign-out. Signing out (or a 401 from the server) also empties the query cache, so one user's watchlist can
 * never be shown to the next user of the same tab.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const [signedOutOnPurpose, setSignedOutOnPurpose] = useState(false)

  const start = useCallback(
    (auth: AuthResponse) => {
      const next = toSession(auth)
      saveSession(next)
      queryClient.clear()
      setSignedOutOnPurpose(false)
      setSession(next)
    },
    [queryClient],
  )

  const logout = useCallback(() => {
    clearSession()
    queryClient.clear()
    setSignedOutOnPurpose(true)
    setSession(null)
  }, [queryClient])

  // api/client.ts announces a rejected token; the stored session is already cleared there.
  useEffect(() => {
    const onExpired = () => {
      queryClient.clear()
      setSession(null)
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [queryClient])

  const updateSession = useCallback((changes: Partial<Pick<Session, 'displayName' | 'region'>>) => {
    setSession((current) => {
      if (!current) return current
      const next = { ...current, ...changes }
      saveSession(next)
      return next
    })
  }, [])

  // a new token for the same person: the query cache stays (it is the same user's data)
  const adoptSession = useCallback((auth: AuthResponse) => {
    const next = toSession(auth)
    saveSession(next)
    setSession(next)
  }, [])

  const value = useMemo<AuthValue>(
    () => ({
      session,
      signedOutOnPurpose,
      login: async (username, password) => start(await authApi.login(username, password)),
      register: async (payload) => start(await authApi.register(payload)),
      logout,
      updateSession,
      adoptSession,
    }),
    [session, signedOutOnPurpose, start, logout, updateSession, adoptSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
