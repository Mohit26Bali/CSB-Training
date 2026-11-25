import type { WatchStatus } from '../api/types'

/**
 * Small shared pieces of the watchlist feature, kept out of the component files so those only export components:
 * the display names of the three statuses (components/WatchlistControl, pages/WatchlistPage) and the check that makes
 * the login redirect safe (pages/LoginPage, RegisterPage).
 */
export const STATUS_LABEL: Record<WatchStatus, string> = { WANT: 'Want to watch', WATCHING: 'Watching', WATCHED: 'Watched' }

/** Where to go after signing in: the ?next= path set by auth/RequireAuth, only if it is a local path (no open redirect). */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}
