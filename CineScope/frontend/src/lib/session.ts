import type { AuthResponse } from '../api/types'

/**
 * The signed-in user's session, kept in sessionStorage (it ends when the tab closes, and is never readable by other
 * sites). Read by api/client.ts to attach the bearer token to every request, and by auth/AuthContext.tsx, which
 * mirrors it into React state. Storage can throw (private windows, blocked cookies), so every access is guarded and the
 * app simply behaves as signed out.
 */
export interface Session {
  token: string
  username: string
  displayName: string
  region: string
  role: 'USER' | 'ADMIN'
  expiresAt: number
}

const KEY = 'cinescope.session'

export function toSession(auth: AuthResponse): Session {
  return {
    token: auth.token,
    username: auth.username,
    displayName: auth.displayName,
    region: auth.region,
    role: auth.role === 'ADMIN' ? 'ADMIN' : 'USER',
    expiresAt: Date.now() + auth.expiresInSeconds * 1000,
  }
}

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    session.role = session.role === 'ADMIN' ? 'ADMIN' : 'USER' // sessions stored before roles existed are ordinary users
    if (!session.token || session.expiresAt <= Date.now()) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(session: Session) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    /* signed in for this page view only */
  }
}

export function clearSession() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* nothing stored */
  }
}

/** Fired by api/client.ts when the server rejects the token, so AuthContext can sign the user out. */
export const SESSION_EXPIRED_EVENT = 'cinescope:session-expired'
