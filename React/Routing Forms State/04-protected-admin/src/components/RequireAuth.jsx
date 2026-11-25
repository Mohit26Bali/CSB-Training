import { Navigate, Outlet, useLocation } from 'react-router-dom'

/**
 * Wraps the protected routes. Logged in: the requested page renders in the Outlet.
 * Not logged in: redirect to /login, remembering where the user wanted to go
 * (location.state.from), so the login page can send them back there.
 */
function RequireAuth({ loggedIn }) {
  const location = useLocation()
  if (!loggedIn) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

export default RequireAuth
