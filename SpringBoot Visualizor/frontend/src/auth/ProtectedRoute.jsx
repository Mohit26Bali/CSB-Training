import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * UX-only guard. Real authorization is enforced by the backend on every request;
 * this just avoids showing pages that would fail with 401/403 anyway.
 */
export default function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <p className="p-6 text-fg-2">Checking your session…</p>
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}
