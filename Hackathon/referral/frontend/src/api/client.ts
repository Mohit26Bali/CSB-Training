import axios, { AxiosError } from 'axios'
import { clearSession, loadSession, SESSION_EXPIRED_EVENT } from '../auth/session'

/** A failed API call, shaped from the backend's ErrorResponse JSON. */
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

export const http = axios.create({ baseURL: '/api', timeout: 15000 })

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
    const isLogin = error.config?.url?.startsWith('/auth/')
    if (status === 401 && !isLogin) {
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
