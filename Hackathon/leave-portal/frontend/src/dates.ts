/** Parses "yyyy-MM-dd" as a local date (new Date("2026-03-02") would be UTC and can shift by a day). */
export function parseDay(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function toIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** Monday to Friday days in [start, end], both included. Mirrors the server's rule, for the live preview only. */
export function workingDays(start: string, end: string): number {
  let count = 0
  const last = parseDay(end)
  for (let d = parseDay(start); d <= last; d.setDate(d.getDate() + 1)) {
    const day = d.getDay()
    if (day !== 0 && day !== 6) count++
  }
  return count
}

export function formatRange(start: string, end: string): string {
  const fmt = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
  return start === end ? fmt.format(parseDay(start)) : `${fmt.format(parseDay(start))} – ${fmt.format(parseDay(end))}`
}
