import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchCurrentUser, loginRequest } from '../api/authApi'
import { setUnauthorizedHandler } from '../api/client'
import { AuthContext } from './authContext'
import { clearToken, getToken, setToken } from './tokenStorage'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // If a token is already stored we must ask /me whether it is still valid before rendering protected pages.
  const [loading, setLoading] = useState(() => getToken() !== null)

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  useEffect(() => {
    if (!getToken()) return
    let cancelled = false
    fetchCurrentUser()
      .then((me) => {
        if (!cancelled) setUser(me)
      })
      .catch(() => {
        if (!cancelled) logout()
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [logout])

  const login = useCallback(
    async (username, password) => {
      const { accessToken } = await loginRequest({ username, password })
      setToken(accessToken)
      try {
        setUser(await fetchCurrentUser())
      } catch (error) {
        logout()
        throw error
      }
    },
    [logout],
  )

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
