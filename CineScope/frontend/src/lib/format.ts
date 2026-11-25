/** Display formatting shared by the pages. */

/** 139 -> "2h 19m"; 45 -> "45m". */
export function formatRuntime(minutes: number | null): string | null {
  if (!minutes || minutes <= 0) return null
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h${m > 0 ? ` ${m}m` : ''}` : `${m}m`
}

/** 63000000 -> "$63,000,000"; null when unknown. */
export function formatMoney(value: number | null): string | null {
  return value && value > 0 ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(value) : null
}

/** TMDB scores are 0-10; show one decimal, or "NR" (not rated) for 0. */
export function formatRating(rating: number): string {
  return rating > 0 ? rating.toFixed(1) : 'NR'
}

/** "2026-09-12" -> "12 Sep 2026". */
export function formatDate(iso: string | null): string | null {
  if (!iso) return null
  const date = new Date(`${iso}T00:00:00`)
  return Number.isNaN(date.getTime()) ? null : date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

/** Age in whole years from ISO dates: at death when {@code died} is given, otherwise today. Null if the birthday is unusable. */
export function ageFrom(born: string | null, died: string | null): number | null {
  if (!born) return null
  const start = new Date(`${born}T00:00:00`)
  const end = died ? new Date(`${died}T00:00:00`) : new Date()
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null
  let age = end.getFullYear() - start.getFullYear()
  if (end.getMonth() < start.getMonth() || (end.getMonth() === start.getMonth() && end.getDate() < start.getDate())) age--
  return age >= 0 ? age : null
}

const languageNames = new Intl.DisplayNames(['en'], { type: 'language' })

/** "hi" -> "Hindi". */
export function formatLanguage(code: string | null): string | null {
  if (!code) return null
  try {
    return languageNames.of(code) ?? code
  } catch {
    return code
  }
}

export function formatCount(value: number): string {
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

/** "top_rated" -> "Top rated". */
export function humanize(value: string): string {
  const text = value.replace(/_/g, ' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}
