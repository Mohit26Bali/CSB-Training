import { createContext } from 'react'
import type { AuthResponse, RegisterPayload } from '../api/types'
import type { Session } from '../lib/session'

/**
 * The shape of the auth context and the context object itself. Kept apart from the provider (AuthContext.tsx) so that
 * file only exports a component; read through auth/useAuth.ts.
 */
export interface AuthValue {
  session: Session | null
  /** True after the person signed out (or deleted their account) on purpose, as opposed to a session that expired. */
  signedOutOnPurpose: boolean
  login: (username: string, password: string) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => void
  /** Changes shown details of the current session (name, country) after the profile was saved. */
  updateSession: (changes: Partial<Pick<Session, 'displayName' | 'region'>>) => void
  /** Replaces the session with a freshly issued one (after a password change, which ends the old token). */
  adoptSession: (auth: AuthResponse) => void
}

export const AuthContext = createContext<AuthValue | null>(null)
