import type { TransactionFilters } from '../api/types'

/**
 * Small helpers around the transaction-history filters (api/types.ts#TransactionFilters), shared by
 * components/TransactionFilterBar (the UI), api/endpoints.ts (the query string) and the pages (empty states).
 */

/** How many filters are switched on (used for the "Filters (2)" badge and the "no results" message). */
export function activeFilterCount(filters: TransactionFilters): number {
  return Object.values(filters).filter((value) => value !== undefined && value !== '').length
}

/** Local calendar date as YYYY-MM-DD, which is what <input type="date"> and the backend's ISO date parameters use. */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export type DatePreset = 'last7' | 'last30' | 'thisMonth'

export const datePresets: { key: DatePreset; label: string }[] = [
  { key: 'last7', label: 'Last 7 days' },
  { key: 'last30', label: 'Last 30 days' },
  { key: 'thisMonth', label: 'This month' },
]

/** The from / to dates (inclusive) of a preset, counted back from today. */
export function presetRange(preset: DatePreset, today = new Date()): { from: string; to: string } {
  const to = toIsoDate(today)
  if (preset === 'thisMonth') return { from: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)), to }
  const days = preset === 'last7' ? 6 : 29
  return { from: toIsoDate(new Date(today.getFullYear(), today.getMonth(), today.getDate() - days)), to }
}

/** Query-string parameters for the history endpoint: only the filters that are set. */
export function toQueryParams(filters: TransactionFilters): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params[key] = value as string | number
  }
  return params
}
