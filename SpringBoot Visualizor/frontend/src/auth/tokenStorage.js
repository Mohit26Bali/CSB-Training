// The access token lives in sessionStorage: it survives a page refresh but is cleared when the tab closes.
// Trade-off: any script running on the page (XSS) could read it. Acceptable for this learning app; a
// production system would prefer short-lived tokens with an httpOnly refresh cookie.
const KEY = 'springlab.accessToken'

export function getToken() {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    sessionStorage.setItem(KEY, token)
  } catch {
    // Storage unavailable (private mode etc.): the user simply has to log in again after a refresh.
  }
}

export function clearToken() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}
