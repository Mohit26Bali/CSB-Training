import type { Role } from '../api/types'

export interface Session {
  token: string
  username: string
  fullname: string
  role: Role
  expiresAt: number
}

const KEY = 'helpdesk.session'

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

/** Fired by the HTTP client when the server says the token is no longer valid. */
export const SESSION_EXPIRED_EVENT = 'helpdesk:session-expired'
