import { useState } from 'react'
import type { TransactionDirection, TransactionFilters, TransactionStatus, TransactionType } from '../api/types'
import { activeFilterCount, datePresets, presetRange } from '../lib/filters'
import { Icon } from './Icon'

/**
 * The filter panel above a transaction list: date range (with presets), type, status, money in / out, and amount
 * range. Used by pages/customer/TransactionsPage and AccountDetailPage, which keep the applied filters in their state
 * and pass them to api/queries.ts#useHistory; the backend does the filtering (dto/TransactionFilter).
 *
 * Dates and dropdowns apply immediately. The two amount boxes apply when the person leaves the box or presses Enter,
 * so typing "1500" does not fire four requests. Inconsistent input (from after to, min above max) is explained under
 * the field and is not sent.
 */
const field =
  'w-full rounded-xl border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-faint focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15'

const labelClass = 'mb-1 block text-xs font-semibold uppercase tracking-wide text-faint'

function Select<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: T | undefined
  onChange: (value: T | undefined) => void
  options: { value: T; label: string }[]
}) {
  return (
    <div>
      <label className={labelClass}>
        {label}
        <div className="relative mt-1 normal-case tracking-normal">
          <select value={value ?? ''} onChange={(e) => onChange((e.target.value || undefined) as T | undefined)} className={`${field} appearance-none pr-9`}>
            <option value="">All</option>
            {options.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <Icon name="chevronDown" className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
        </div>
      </label>
    </div>
  )
}

export function TransactionFilterBar({ value, onChange }: { value: TransactionFilters; onChange: (filters: TransactionFilters) => void }) {
  const [open, setOpen] = useState(false)
  // Amount boxes keep their own text until blur / Enter.
  const [minText, setMinText] = useState(value.minAmount?.toString() ?? '')
  const [maxText, setMaxText] = useState(value.maxAmount?.toString() ?? '')
  const [amountError, setAmountError] = useState<string | undefined>()

  const count = activeFilterCount(value)
  const dateError = value.from && value.to && value.from > value.to ? "'From' must not be after 'To'" : undefined

  const set = (patch: Partial<TransactionFilters>) => onChange({ ...value, ...patch })

  function applyAmounts() {
    const parse = (text: string) => (text.trim() === '' ? undefined : Number(text.replace(/,/g, '')))
    const min = parse(minText)
    const max = parse(maxText)
    if ((min !== undefined && (Number.isNaN(min) || min < 0)) || (max !== undefined && (Number.isNaN(max) || max < 0))) {
      return setAmountError('Enter amounts as positive numbers')
    }
    if (min !== undefined && max !== undefined && min > max) return setAmountError('Minimum must not be above maximum')
    setAmountError(undefined)
    if (min !== value.minAmount || max !== value.maxAmount) set({ minAmount: min, maxAmount: max })
  }

  function clear() {
    setMinText('')
    setMaxText('')
    setAmountError(undefined)
    onChange({})
  }

  const presetActive = (key: (typeof datePresets)[number]['key']) => {
    const range = presetRange(key)
    return value.from === range.from && value.to === range.to
  }

  return (
    <div className="mb-4 rounded-2xl border border-line bg-surface-2">
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          aria-expanded={open}
          aria-controls="transaction-filters"
          className="flex items-center gap-2 text-sm font-semibold text-ink"
        >
          <Icon name="search" className="size-4 text-faint" />
          Filters
          {count > 0 && (
            <span className="grid min-w-5 place-items-center rounded-full bg-brand-600 px-1.5 text-xs font-semibold text-white">{count}</span>
          )}
          <Icon name="chevronDown" className={`size-4 text-faint transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        <div className="hidden flex-wrap items-center gap-2 sm:flex">
          {datePresets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              onClick={() => (presetActive(preset.key) ? set({ from: undefined, to: undefined }) : set(presetRange(preset.key)))}
              aria-pressed={presetActive(preset.key)}
              className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
                presetActive(preset.key)
                  ? 'border-brand-500 bg-brand-500/10 text-brand-700 dark:text-brand-200'
                  : 'border-line text-muted hover:border-brand-400 hover:text-ink'
              }`}
            >
              {preset.label}
            </button>
          ))}
        </div>
        {count > 0 && (
          <button type="button" onClick={clear} className="text-sm font-semibold text-brand-600 hover:underline dark:text-brand-300">
            Clear all
          </button>
        )}
      </div>

      {open && (
        <div id="transaction-filters" className="fade-in grid gap-4 border-t border-line px-4 py-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <label className={labelClass} htmlFor="filter-from">
              From
            </label>
            <input id="filter-from" type="date" value={value.from ?? ''} max={value.to} onChange={(e) => set({ from: e.target.value || undefined })} className={field} />
          </div>
          <div>
            <label className={labelClass} htmlFor="filter-to">
              To
            </label>
            <input id="filter-to" type="date" value={value.to ?? ''} min={value.from} onChange={(e) => set({ to: e.target.value || undefined })} className={field} />
          </div>
          <Select<TransactionType>
            label="Type"
            value={value.type}
            onChange={(type) => set({ type })}
            options={[
              { value: 'DEPOSIT', label: 'Deposit' },
              { value: 'WITHDRAWAL', label: 'Withdrawal' },
              { value: 'TRANSFER', label: 'Transfer' },
            ]}
          />
          <Select<TransactionStatus>
            label="Status"
            value={value.status}
            onChange={(status) => set({ status })}
            options={[
              { value: 'SUCCESS', label: 'Success' },
              { value: 'FAILED', label: 'Failed' },
            ]}
          />
          <Select<TransactionDirection>
            label="Money"
            value={value.direction}
            onChange={(direction) => set({ direction })}
            options={[
              { value: 'CREDIT', label: 'Money in' },
              { value: 'DEBIT', label: 'Money out' },
            ]}
          />
          <div>
            <label className={labelClass} htmlFor="filter-min">
              Min amount (₹)
            </label>
            <input
              id="filter-min"
              inputMode="decimal"
              value={minText}
              placeholder="0"
              onChange={(e) => setMinText(e.target.value)}
              onBlur={applyAmounts}
              onKeyDown={(e) => e.key === 'Enter' && applyAmounts()}
              className={field}
            />
          </div>
          <div>
            <label className={labelClass} htmlFor="filter-max">
              Max amount (₹)
            </label>
            <input
              id="filter-max"
              inputMode="decimal"
              value={maxText}
              placeholder="No limit"
              onChange={(e) => setMaxText(e.target.value)}
              onBlur={applyAmounts}
              onKeyDown={(e) => e.key === 'Enter' && applyAmounts()}
              className={field}
            />
          </div>
          {(dateError || amountError) && (
            <p role="alert" className="text-sm text-rose-600 sm:col-span-2 lg:col-span-4 dark:text-rose-400">
              {dateError ?? amountError}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
