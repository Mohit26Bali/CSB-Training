import { NavLink } from 'react-router-dom'
import { useModerationQueue } from '../api/queries'

/**
 * The two sections of the administrator area, shown at the top of pages/AdminUsersPage and pages/AdminModerationPage: the
 * accounts, and the moderation queue with a count of comments waiting for a decision (from the same query the queue page uses,
 * so the number is always the real one).
 */
export function AdminTabs() {
  const waiting = useModerationQueue('PENDING', 1).data?.totalResults ?? 0
  const link = ({ isActive }: { isActive: boolean }) =>
    `inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
      isActive ? 'border-ember-500 bg-ember-500 text-white' : 'border-line text-muted hover:border-ember-400 hover:text-ink'
    }`
  return (
    <nav aria-label="Admin sections" className="mb-6 flex flex-wrap gap-2">
      <NavLink to="/admin/users" className={link}>
        Accounts
      </NavLink>
      <NavLink to="/admin/moderation" className={link}>
        Moderation
        {waiting > 0 && (
          <span data-testid="moderation-count" aria-label={`${waiting} waiting`} className="grid min-w-5 place-items-center rounded-full bg-white/90 px-1.5 text-xs font-bold text-black">
            {waiting}
          </span>
        )}
      </NavLink>
    </nav>
  )
}
