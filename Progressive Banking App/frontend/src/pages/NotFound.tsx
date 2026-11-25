import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { homePathFor } from '../auth/homePath'
import { LogoMark } from '../components/Brand'

/** Shown for any URL no route matches (see App.tsx). Links back to the right home page for the current user. */
export function NotFound() {
  const { user } = useAuth()
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 px-4 text-center">
      <LogoMark className="size-14" />
      <p className="mt-2 text-6xl font-semibold tracking-tight text-brand-600 dark:text-brand-300">404</p>
      <p className="text-muted">That page doesn’t exist, or it has moved.</p>
      <Link
        to={user ? homePathFor(user.role) : '/login'}
        className="mt-2 rounded-xl bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
      >
        {user ? 'Back to dashboard' : 'Go to sign in'}
      </Link>
    </div>
  )
}
