import axios, { AxiosError } from 'axios'
import { SESSION_EXPIRED_EVENT, clearSession, loadSession } from '../lib/session'

/**
 * The one configured HTTP client; every request in api/endpoints.ts goes through it. The base URL '/api' is forwarded to
 * the Spring Boot backend by the Vite dev-server proxy (vite.config.ts). The response interceptor turns the backend's
 * ErrorResponse JSON (status, message) into an ApiError, so pages can show a meaningful message.
 */
export class ApiError extends Error {
  status: number
  /** Per-field messages when the server rejected the input (for example the new password), keyed by the field name. */
  fieldErrors: Record<string, string>

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const http = axios.create({ baseURL: '/api', timeout: 20000 })

// Attach the signed-in user's token (lib/session.ts) to every request; public endpoints simply ignore it.
http.interceptors.request.use((config) => {
  const session = loadSession()
  if (session) config.headers.set('Authorization', `Bearer ${session.token}`)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; fieldErrors?: Record<string, string> }>) => {
    if (!error.response) {
      return Promise.reject(new ApiError('Cannot reach the server. Check your connection and try again.', 0))
    }
    const { status, data } = error.response
    // A 401 on a request that carried a token means it expired or was revoked: sign out everywhere (auth/AuthContext).
    // A 401 from the login form itself (wrong password) has no session and just shows its message.
    if (status === 401 && loadSession()) {
      clearSession()
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }
    return Promise.reject(new ApiError(data?.message ?? 'Something went wrong. Please try again.', status, data?.fieldErrors ?? {}))
  },
)

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}
