import { Link, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * UX-only role gate (render inside ProtectedRoute). It only avoids showing a page that would fail;
 * every API call behind it is authorized again by the backend.
 */
export default function RequireRole({ roles }) {
  const { user } = useAuth()
  if (!roles.includes(user.role)) {
    return (
      <main className="p-6">
        <h1 className="text-xl font-bold">403 - Not allowed</h1>
        <p className="mt-2 text-fg-2">Your role ({user.role}) cannot open this page.</p>
        <Link to="/" className="mt-4 inline-block text-accent hover:underline">
          Back to dashboard
        </Link>
      </main>
    )
  }
  return <Outlet />
}
