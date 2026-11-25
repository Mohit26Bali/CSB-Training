/**
 * Display formatting shared by every page. Money is always shown in Indian rupees with two decimals.
 * The backend sends amounts as JSON numbers with two decimals; they are only ever displayed or summed in whole
 * paise (see sumMoney), never accumulated as floating point.
 */

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const inrCompact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

export function formatMoney(value: number): string {
  return inr.format(value)
}

/** Whole rupees only, for large headline numbers where paise are noise. */
export function formatMoneyShort(value: number): string {
  return inrCompact.format(value)
}

/** Adds amounts in paise so 0.1 + 0.2 style float errors never reach the screen. */
export function sumMoney(values: number[]): number {
  return values.reduce((total, v) => total + Math.round(v * 100), 0) / 100
}

/** "1071103934" -> "1071 1039 34" */
export function groupAccountNumber(accountNumber: string): string {
  return accountNumber.replace(/(\d{4})(?=\d)/g, '$1 ')
}

/** "1071103934" -> "•••• 3934" */
export function maskAccountNumber(accountNumber: string): string {
  return `•••• ${accountNumber.slice(-4)}`
}

const dateFormat = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
const timeFormat = new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true })

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

export function formatTime(iso: string): string {
  return timeFormat.format(new Date(iso)).toUpperCase()
}

export function formatDateTime(iso: string): string {
  return `${formatDate(iso)}, ${formatTime(iso)}`
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

export function greeting(date = new Date()): string {
  const hour = date.getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
