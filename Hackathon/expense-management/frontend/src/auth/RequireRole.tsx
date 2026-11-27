import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { config } from '../config'
import { useAuth } from './AuthContext'

export function homePathFor(role: Role): string {
  return config.homePath[role]
}

/** Route guard: sends anonymous visitors to login and people with the wrong role to their own home page. */
export function RequireRole({ role }: { role: Role | Role[] }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  const allowed = Array.isArray(role) ? role : [role]
  if (!allowed.includes(user.role)) return <Navigate to={homePathFor(user.role)} replace />
  return <Outlet />
}
