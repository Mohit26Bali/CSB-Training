import { Link, Outlet } from 'react-router-dom'
import { EmptyState } from '../components/ui'
import { buttonClass } from '../lib/buttonStyles'
import { useAuth } from './useAuth'

/**
 * Route guard used in App.tsx around the administrator pages (/admin/...). It sits INSIDE auth/RequireAuth, so the person is
 * already signed in; if their account is not an administrator they see a plain "no access" page instead of an empty screen of
 * failed requests. This is only courtesy: the server refuses every /api/admin/ call from a non-administrator with 403
 * (config/SecurityConfig and service/AdminUserService), whatever this page does.
 */
export function RequireAdmin() {
  const { session } = useAuth()
  if (session?.role !== 'ADMIN') {
    return (
      <div className="px-4 pt-28">
        <EmptyState icon="film" title="You don’t have access to this page" hint="Only administrators can manage accounts." />
        <div className="mt-6 text-center">
          <Link to="/" className={buttonClass('secondary')}>
            Back to the home page
          </Link>
        </div>
      </div>
    )
  }
  return <Outlet />
}
