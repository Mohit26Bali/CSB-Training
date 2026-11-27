/** Small date helpers. Dates travel as "YYYY-MM-DD" and date-times as "YYYY-MM-DDTHH:mm:ss" (local time, no zone). */

export function isoDay(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function parseDay(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(iso: string, days: number): string {
  const d = parseDay(iso)
  d.setDate(d.getDate() + days)
  return isoDay(d)
}

/** "10:30 am" from "2026-11-02T10:30:00" */
export function formatTime(dateTime: string): string {
  const [h, m] = dateTime.slice(11, 16).split(':').map(Number)
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(2000, 0, 1, h, m))
}

/** "Mon" */
export function weekday(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(parseDay(iso))
}

/** "Mon, 2 Nov" */
export function longDay(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' }).format(parseDay(iso))
}
