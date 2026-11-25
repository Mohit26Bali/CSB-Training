import type { ApiErrorBody } from './types'

const TOKEN_KEY = 'cartnest.token'

/** An error answered by the API, with its message and any per-field validation messages. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Called when the API says the login is no longer valid (401 on a request that sent a token). */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** fetch() for the CartNest API: JSON in and out, the login token attached, errors turned into ApiError. */
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = tokenStore.get()
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'Cannot reach CartNest. Check your connection and that the server is running.')
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  const data = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    const body = data as Partial<ApiErrorBody> | undefined
    if (response.status === 401 && token) onUnauthorized()
    throw new ApiError(response.status, body?.message ?? `Request failed (${response.status})`, body?.fieldErrors ?? {})
  }
  return data as T
}
