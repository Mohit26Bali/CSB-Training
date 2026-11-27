/**
 * Remembers, in this browser only, which lesson a user opened last so the dashboard can offer "Continue learning".
 * It is a convenience, not progress: it says nothing about what the learner completed. Storage can be unavailable
 * (private windows, blocked storage); every access is guarded and the dashboard works without it.
 */
const key = (userId) => `springlab.lastLesson.${userId}`

export function readLastLesson(userId) {
  try {
    const raw = window.localStorage.getItem(key(userId))
    const parsed = raw ? JSON.parse(raw) : null
    return parsed && typeof parsed.id === 'string' ? parsed : null
  } catch {
    return null
  }
}

export function writeLastLesson(userId, id) {
  try {
    window.localStorage.setItem(key(userId), JSON.stringify({ id, at: Date.now() }))
  } catch {
    // storage unavailable: nothing to remember
  }
}
