/**
 * Holds the login token for the API client. F0 keeps it in memory only; F1 replaces this with the auth context
 * and decides how a refresh is survived (FRONTEND-PLAN, open question about token storage).
 * The client reads the token from here, and tells this module when the server answered 401 (expired or invalid).
 */
let token: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export const session = {
  getToken: (): string | null => token,
  setToken: (value: string | null): void => {
    token = value;
  },
  /** F1 registers a function here that sends the user back to the login page. */
  onUnauthorized: (handler: (() => void) | null): void => {
    unauthorizedHandler = handler;
  },
  notifyUnauthorized: (): void => {
    token = null;
    unauthorizedHandler?.();
  },
};
