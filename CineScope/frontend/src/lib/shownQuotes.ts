/**
 * Remembers which famous lines the sign-in screens have already shown this browser, so the next visit shows different ones.
 * api/queries (useShowcase) reads the ids before asking GET /api/showcase and sends them as ?exclude=; the server
 * (backend QuoteService.sample) then avoids them. Only the last MAX ids are kept: once a browser has seen nearly everything, the
 * oldest are forgotten and come round again.
 *
 * This is a convenience, not a record: private windows, blocked site data and quota errors make storage throw, and then the
 * screens simply show a fresh random mix each time. Every access is wrapped for that reason.
 */
const KEY = 'cinescope.quotes.shown'
const MAX = 300

export function shownQuoteIds(): number[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((n): n is number => Number.isInteger(n)) : []
  } catch {
    return []
  }
}

export function rememberQuoteIds(ids: number[]): void {
  try {
    const merged = [...shownQuoteIds().filter((id) => !ids.includes(id)), ...ids]
    localStorage.setItem(KEY, JSON.stringify(merged.slice(-MAX)))
  } catch {
    // storage unavailable: nothing to remember, nothing to break
  }
}
