import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { errorMessage } from '../../api/client'
import { managerApi } from '../../api/endpoints'
import type { Leave } from '../../api/types'
import { Button, ErrorState, LoadingState, PageHeader } from '../../components/ui'
import { formatRange, parseDay, toIso } from '../../dates'

const typeColour: Record<string, string> = {
  ANNUAL: 'bg-brand-100 text-brand-800',
  SICK: 'bg-rose-100 text-rose-800',
  CASUAL: 'bg-amber-100 text-amber-800',
  UNPAID: 'bg-slate-100 text-slate-600',
}

const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function monthKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** All cells of the month grid, Monday first, padded with the neighbouring months' days. */
function gridDays(first: Date): Date[] {
  const start = new Date(first)
  start.setDate(first.getDate() - ((first.getDay() + 6) % 7))
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
  const days: Date[] = []
  for (let d = new Date(start); d <= last || d.getDay() !== 1; d.setDate(d.getDate() + 1)) days.push(new Date(d))
  return days
}

export function CalendarPage() {
  const [first, setFirst] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const key = monthKey(first)
  const calendar = useQuery({ queryKey: ['manager', 'calendar', key], queryFn: () => managerApi.calendar(key) })

  const days = useMemo(() => gridDays(first), [first])
  const byDay = useMemo(() => {
    const map = new Map<string, Leave[]>()
    for (const l of calendar.data ?? []) {
      for (let d = parseDay(l.startDate); d <= parseDay(l.endDate); d.setDate(d.getDate() + 1)) {
        const day = d.getDay()
        if (day === 0 || day === 6) continue
        const iso = toIso(d)
        map.set(iso, [...(map.get(iso) ?? []), l])
      }
    }
    return map
  }, [calendar.data])

  const shift = (months: number) => setFirst((f) => new Date(f.getFullYear(), f.getMonth() + months, 1))
  const today = toIso(new Date())
  const title = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(first)

  return (
    <>
      <PageHeader
        title="Team calendar"
        subtitle="Who is away, based on approved leave."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" small onClick={() => shift(-1)} aria-label="Previous month">
              ←
            </Button>
            <span className="min-w-36 text-center text-sm font-medium text-slate-800" aria-live="polite">
              {title}
            </span>
            <Button variant="secondary" small onClick={() => shift(1)} aria-label="Next month">
              →
            </Button>
            <Button variant="ghost" small onClick={() => setFirst(new Date(new Date().getFullYear(), new Date().getMonth(), 1))}>
              Today
            </Button>
          </div>
        }
      />

      {calendar.isPending ? (
        <LoadingState />
      ) : calendar.isError ? (
        <ErrorState message={errorMessage(calendar.error)} onRetry={() => calendar.refetch()} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="grid min-w-[44rem] grid-cols-7 border-b border-slate-200 bg-slate-100 text-xs font-medium uppercase tracking-wide text-slate-500">
              {weekdays.map((w) => (
                <div key={w} className="px-2 py-2">
                  {w}
                </div>
              ))}
            </div>
            <div className="grid min-w-[44rem] grid-cols-7">
              {days.map((d) => {
                const iso = toIso(d)
                const inMonth = d.getMonth() === first.getMonth()
                const weekend = d.getDay() === 0 || d.getDay() === 6
                const away = byDay.get(iso) ?? []
                return (
                  <div
                    key={iso}
                    className={`min-h-24 border-b border-r border-slate-200 p-1.5 ${weekend ? 'bg-slate-50' : ''} ${inMonth ? '' : 'opacity-40'}`}
                  >
                    <p className={`mb-1 text-xs ${iso === today ? 'inline-grid size-5 place-items-center rounded-full bg-brand-700 font-semibold text-white' : 'text-slate-500'}`}>
                      {d.getDate()}
                    </p>
                    <ul className="space-y-0.5">
                      {away.slice(0, 3).map((l) => (
                        <li
                          key={l.id}
                          title={`${l.employeeName}: ${l.leaveTypeLabel}`}
                          className={`truncate rounded px-1.5 py-0.5 text-xs font-medium ${typeColour[l.leaveType] ?? typeColour.UNPAID}`}
                        >
                          {l.employeeName}
                        </li>
                      ))}
                      {away.length > 3 && <li className="px-1 text-xs text-slate-500">+{away.length - 3} more</li>}
                    </ul>
                  </div>
                )
              })}
            </div>
          </div>

          <ul className="mt-4 flex flex-wrap gap-3 text-xs text-slate-600" aria-label="Legend">
            {Object.entries(typeColour).map(([t, cls]) => (
              <li key={t} className="flex items-center gap-1.5">
                <span className={`inline-block size-3 rounded ${cls.split(' ')[0]}`} />
                {t.charAt(0) + t.slice(1).toLowerCase()}
              </li>
            ))}
          </ul>

          <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Approved leave this month</h2>
          {(calendar.data ?? []).length === 0 ? (
            <p className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
              Nobody is away this month.
            </p>
          ) : (
            <ul className="space-y-2">
              {(calendar.data ?? []).map((l) => (
                <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm">
                  <span className="font-medium text-slate-900">{l.employeeName}</span>
                  <span className="text-slate-600">{formatRange(l.startDate, l.endDate)}</span>
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${typeColour[l.leaveType] ?? typeColour.UNPAID}`}>
                    {l.leaveTypeLabel}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </>
  )
}
