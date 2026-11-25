import axios, { AxiosError } from 'axios'
import { clearSession, loadSession, SESSION_EXPIRED_EVENT } from '../auth/session'

/**
 * The one configured HTTP client. Every request in api/endpoints.ts goes through it.
 *  - base URL '/api' is forwarded to Spring Boot by the Vite dev-server proxy (vite.config.ts);
 *  - the request interceptor adds the JWT from auth/session.ts;
 *  - the response interceptor turns the backend's ErrorResponse JSON into an ApiError, and announces an expired
 *    session (401) so auth/AuthContext can sign the user out.
 */

/** A failed API call, shaped from the backend's ErrorResponse JSON (dto/ErrorResponse). */
export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface ErrorBody {
  message?: string
  fieldErrors?: Record<string, string>
}

export const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((config) => {
  const session = loadSession()
  if (session) config.headers.set('Authorization', `Bearer ${session.token}`)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorBody>) => {
    if (!error.response) {
      return Promise.reject(new ApiError('Cannot reach the server. Check your connection and try again.', 0))
    }
    const { status, data } = error.response
    const isAuthCall = error.config?.url?.startsWith('/auth/login') || error.config?.url?.startsWith('/auth/register')
    if (status === 401 && !isAuthCall) {
      clearSession()
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }
    return Promise.reject(
      new ApiError(data?.message ?? 'Something went wrong. Please try again.', status, data?.fieldErrors ?? {}),
    )
  },
)

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {}
}
