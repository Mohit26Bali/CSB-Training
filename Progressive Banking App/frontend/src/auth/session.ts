import type { Role } from '../api/types'

/**
 * The login session kept in sessionStorage (cleared when the tab closes).
 * Read by api/client.ts (to attach the JWT) and auth/AuthContext (to restore the user after a page reload).
 * Trade-off: sessionStorage is readable by scripts on the page; an httpOnly cookie would be safer but needs
 * backend changes. The token is short-lived (30 minutes) to limit the damage of any leak.
 */
export interface Session {
  token: string
  username: string
  fullName: string
  role: Role
  expiresAt: number
}

const KEY = 'bank.session'

// sessionStorage can throw (private windows, blocked storage), so the app must work without it.
export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    return session.expiresAt > Date.now() ? session : null
  } catch {
    return null
  }
}

export function saveSession(session: Session): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    /* in-memory state still works for this tab */
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* nothing to clear */
  }
}

/** Fired by api/client.ts when the server says the token is no longer valid. */
export const SESSION_EXPIRED_EVENT = 'bank:session-expired'
