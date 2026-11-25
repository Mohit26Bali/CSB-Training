import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from './AuthContext'
import { homePathFor } from './homePath'

/**
 * Route guard used in App.tsx: sends anonymous visitors to login and people with the wrong role to their own home
 * page. This only improves the experience; the real enforcement is on the server (config/SecurityConfig and the
 * service-level @PreAuthorize rules), so editing this file in the browser grants no access.
 */
export function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.role !== role) return <Navigate to={homePathFor(user.role)} replace />
  return <Outlet />
}
