import axios from 'axios'
import { getToken } from '../auth/tokenStorage'

// Relative base URL: in dev the Vite proxy forwards /api to Spring Boot.
const client = axios.create({
  baseURL: '/',
  headers: { 'Content-Type': 'application/json' },
})

let unauthorizedHandler = null

/** AuthProvider registers a callback that clears the session when the API answers 401. */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginCall = error.config?.url === '/api/auth/login'
    // A 401 on /login just means "wrong credentials"; anywhere else it means the token is missing/expired.
    if (error.response?.status === 401 && !isLoginCall && unauthorizedHandler) {
      unauthorizedHandler()
    }
    return Promise.reject(error)
  },
)

export default client
