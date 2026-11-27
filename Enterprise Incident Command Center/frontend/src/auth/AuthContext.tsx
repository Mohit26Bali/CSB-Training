import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { assignmentApi, unwrap } from '../api/client';
import { session } from '../api/session';
import { clearAuth, loadAuth, saveAuth, type AuthUser, type StoredAuth } from './storage';

/**
 * Who is logged in, and the two actions login and logout. Every screen reads this through useAuth().
 *
 * - The token is also handed to src/api/session.ts, which the API client reads for every request.
 * - A 401 from any call (reported by the client) or the end of the token's one hour logs the user out with the
 *   message "Your session expired".
 * - Logging out clears everything that was loaded for that user (TanStack Query cache) and the stored login.
 */
interface AuthValue {
  user: AuthUser | null;
  /** A message for the login page, for example after the session expired. */
  notice: string | null;
  /** True after the user pressed Log out. Then the login page must not send the next person back to this user's page. */
  loggedOutByUser: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function toStoredAuth(answer: Awaited<ReturnType<typeof requestLogin>>): StoredAuth {
  // The contract says exactly which fields are always there (D24), so the answer needs no defensive checks.
  const { token, user, expiresInSeconds } = answer;
  return {
    token,
    expiresAt: Date.now() + expiresInSeconds * 1000,
    user: { id: user.id, username: user.username, fullName: user.fullName, role: user.role, teamId: user.teamId },
  };
}

function requestLogin(username: string, password: string) {
  return unwrap(assignmentApi.POST('/api/auth/login', { body: { username, password } }));
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [auth, setAuth] = useState<StoredAuth | null>(() => {
    const stored = loadAuth();
    session.setToken(stored?.token ?? null); // before the first request of the page
    return stored;
  });
  const [notice, setNotice] = useState<string | null>(null);
  const [loggedOutByUser, setLoggedOutByUser] = useState(false);

  const endSession = useCallback(
    (message: string | null) => {
      session.setToken(null);
      clearAuth();
      queryClient.clear();
      setAuth(null);
      setNotice(message);
    },
    [queryClient],
  );

  const logout = useCallback(() => {
    setLoggedOutByUser(true);
    endSession(null);
  }, [endSession]);

  // A 401 on any call means the token is missing, invalid or expired.
  useEffect(() => {
    session.onUnauthorized(() => endSession('Your session expired. Please log in again.'));
    return () => session.onUnauthorized(null);
  }, [endSession]);

  // The token lives for one hour: log out when it ends instead of waiting for a failing request.
  useEffect(() => {
    if (!auth) return undefined;
    const wait = Math.min(Math.max(auth.expiresAt - Date.now(), 0), 2 ** 31 - 1);
    const timer = window.setTimeout(() => endSession('Your session expired. Please log in again.'), wait);
    return () => window.clearTimeout(timer);
  }, [auth, endSession]);

  const login = useCallback(async (username: string, password: string) => {
    const next = toStoredAuth(await requestLogin(username, password));
    session.setToken(next.token);
    saveAuth(next);
    setNotice(null);
    setLoggedOutByUser(false);
    setAuth(next);
  }, []);

  const value = useMemo<AuthValue>(() => ({ user: auth?.user ?? null, notice, loggedOutByUser, login, logout }), [auth, notice, loggedOutByUser, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
