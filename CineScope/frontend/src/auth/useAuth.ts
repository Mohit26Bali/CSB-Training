import { useContext } from 'react'
import { AuthContext, type AuthValue } from './authState'

/** Access to the signed-in session and login / register / logout. Must be used under {@code AuthProvider} (main.tsx). */
export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
