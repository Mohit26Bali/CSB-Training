import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, setUnauthorizedHandler, tokenStore } from '../api/client'
import * as endpoints from '../api/endpoints'
import type { AuthResponse, User } from '../api/types'

interface AuthState {
  user: User | null
  /** True while a saved login is being checked on start-up. */
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (body: { fullName: string; email: string; password: string; phone?: string }) => Promise<User>
  logout: () => void
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

/**
 * Keeps the logged-in user. The token is kept in localStorage so the login survives a page reload; on start-up it
 * is checked with /auth/me. Any 401 for a request that carried the token logs the user out.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) return
    let cancelled = false
    // Only a 401 (expired or revoked login) logs the user out; the API client's 401 handler does that. A failed
    // connection (server restarting, a request cut off by a quick reload) is retried, so it never loses the login.
    const check = (attempt: number) => {
      endpoints
        .getMe()
        .then((me) => {
          if (!cancelled) {
            setUser(me)
            setLoading(false)
          }
        })
        .catch((error) => {
          if (cancelled) return
          if (error instanceof ApiError && error.status === 0 && attempt < 3) {
            setTimeout(() => check(attempt + 1), 1000 * (attempt + 1))
          } else {
            setLoading(false)
          }
        })
    }
    check(0)
    return () => {
      cancelled = true
    }
  }, [logout])

  const accept = useCallback(
    (auth: AuthResponse) => {
      tokenStore.set(auth.token)
      queryClient.clear()
      setUser(auth.user)
      return auth.user
    },
    [queryClient],
  )

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: (email, password) => endpoints.login(email, password).then(accept),
      register: (body) => endpoints.register(body).then(accept),
      logout,
      setUser,
    }),
    [user, loading, accept, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
