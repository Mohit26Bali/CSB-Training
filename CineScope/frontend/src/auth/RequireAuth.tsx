import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * Route guard used in App.tsx around every page except the sign-in screens: CineScope shows nothing until someone signs in.
 * Anonymous visitors are sent to /login with {@code ?next=} holding where they were going, so pages/LoginPage can return them
 * there afterwards (opening the site root just goes to a plain /login). The server enforces the same rule on its side
 * (config/SecurityConfig); this saves the visitor a screen of failing requests. A rejected or expired token also lands here:
 * api/client.ts signs the user out and this guard takes over.
 */
export function RequireAuth() {
  const { session, signedOutOnPurpose } = useAuth()
  const location = useLocation()
  if (!session) {
    const target = location.pathname + location.search
    // someone who just signed out (or deleted their account) is not sent back to where they were after the next sign-in
    return <Navigate to={target === '/' || signedOutOnPurpose ? '/login' : `/login?next=${encodeURIComponent(target)}`} replace />
  }
  return <Outlet />
}
