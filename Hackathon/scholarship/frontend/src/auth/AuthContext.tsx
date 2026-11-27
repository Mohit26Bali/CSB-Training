import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/endpoints'
import type { AuthResponse, RegisterInput } from '../api/types'
import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT, type Session } from './session'

interface AuthContextValue {
  user: Session | null
  login: (username: string, password: string) => Promise<Session>
  register: (input: RegisterInput) => Promise<Session>
  logout: () => void
  /** True when the user was signed out because the token expired, so the login page can explain why. */
  expired: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

function toSession(auth: AuthResponse): Session {
  return {
    token: auth.token,
    username: auth.username,
    fullname: auth.fullname,
    role: auth.role,
    expiresAt: Date.now() + auth.expiresIn * 1000,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<Session | null>(() => loadSession())
  const [expired, setExpired] = useState(false)

  const signOut = useCallback(
    (becauseExpired: boolean) => {
      clearSession()
      setUser(null)
      setExpired(becauseExpired)
      queryClient.clear() // never show one user's cached data to the next
    },
    [queryClient],
  )

  const start = useCallback((auth: AuthResponse) => {
    const session = toSession(auth)
    saveSession(session)
    setUser(session)
    setExpired(false)
    return session
  }, [])

  const login = useCallback((username: string, password: string) => authApi.login(username, password).then(start), [start])
  const register = useCallback((input: RegisterInput) => authApi.register(input).then(start), [start])
  const logout = useCallback(() => signOut(false), [signOut])

  // The server rejected our token (401).
  useEffect(() => {
    const onExpired = () => signOut(true)
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [signOut])

  // Sign out on schedule, so an idle tab does not sit on a dead token.
  useEffect(() => {
    if (!user) return
    const timer = window.setTimeout(() => signOut(true), Math.max(user.expiresAt - Date.now(), 0))
    return () => window.clearTimeout(timer)
  }, [user, signOut])

  const value = useMemo(() => ({ user, login, register, logout, expired }), [user, login, register, logout, expired])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
