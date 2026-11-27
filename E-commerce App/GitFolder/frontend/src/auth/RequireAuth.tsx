import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { PageSpinner } from '../components/ui'
import { useAuth } from './AuthContext'

export function RequireAuth({ role }: { role?: Role }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <PageSpinner />
  if (!user) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  }
  if (role && user.role !== role) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />
  return <Outlet />
}
