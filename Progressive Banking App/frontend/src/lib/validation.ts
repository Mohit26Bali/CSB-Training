/**
 * Client-side input checks that mirror the backend's Bean Validation rules (backend dto/ package), so people see
 * mistakes immediately. The server remains the authority: it re-checks everything, and its field messages are shown
 * by the forms when they differ (see api/client.ts#fieldErrorsOf).
 */

export type Errors<K extends string = string> = Partial<Record<K, string>>

/** Parses an amount typed by a person: positive, at most 2 decimals. Returns the number or an error message. */
export type ParsedAmount = { ok: true; value: number } | { ok: false; error: string }

export function parseAmount(text: string): ParsedAmount {
  const cleaned = text.replace(/,/g, '').trim()
  if (!cleaned) return { ok: false, error: 'Enter an amount' }
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(cleaned)) return { ok: false, error: 'Use numbers only, with at most 2 decimals' }
  const value = Number(cleaned)
  if (value <= 0) return { ok: false, error: 'Amount must be greater than zero' }
  return { ok: true, value }
}

export function validateUsername(value: string): string | undefined {
  if (value.length < 3 || value.length > 50) return 'Username must be 3 to 50 characters'
  if (!/^[a-zA-Z0-9._-]+$/.test(value)) return "Use letters, digits, '.', '_' or '-' only"
}

export function validateEmail(value: string): string | undefined {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)) return 'Enter a valid email address'
}

export function validateMobile(value: string): string | undefined {
  if (!/^[6-9][0-9]{9}$/.test(value)) return 'Mobile number must be 10 digits starting with 6-9'
}
