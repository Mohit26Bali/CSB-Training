/**
 * Keeps the login in the browser's sessionStorage (FRONTEND-PLAN, decision D27): it survives a page refresh and is
 * cleared when the tab is closed. The token lives for one hour on the server, so this is the shortest storage that
 * does not log the user out on every refresh.
 *
 * Browser storage can be blocked or full, so every access is wrapped: if it fails, the app still works, the user
 * just has to log in again after a refresh.
 */
import type { Role } from './access';

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  teamId: number | null;
}

export interface StoredAuth {
  token: string;
  user: AuthUser;
  /** When the token stops being valid, in milliseconds since 1970 (from expiresInSeconds of the login answer). */
  expiresAt: number;
}

const KEY = 'icc.auth';
const ROLES: Role[] = ['EMPLOYEE', 'SUPPORT_ENGINEER', 'ADMIN'];

export function saveAuth(auth: StoredAuth): void {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(auth));
  } catch {
    /* storage blocked: keep working in memory only */
  }
}

export function clearAuth(): void {
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    /* nothing to do */
  }
}

/** Returns the stored login, or null if there is none, it is damaged, or the token has expired. */
export function loadAuth(): StoredAuth | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<StoredAuth>;
    const user = value.user;
    const valid =
      typeof value.token === 'string' &&
      typeof value.expiresAt === 'number' &&
      !!user &&
      typeof user.id === 'number' &&
      typeof user.username === 'string' &&
      ROLES.includes(user.role as Role);
    if (!valid || (value.expiresAt as number) <= Date.now()) {
      clearAuth();
      return null;
    }
    return value as StoredAuth;
  } catch {
    return null;
  }
}
