import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Span } from '../../api/types'
import { Button, ErrorState, LoadingState, PageHeader, statusLabel } from '../../components/ui'

const monthKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`

function columns(span: Span, first: string, days: number) {
  const monthStart = new Date(first + 'T00:00')
  const inDate = new Date(span.checkIn + 'T00:00')
  const outDate = new Date(span.checkOut + 'T00:00')
  const monthEnd = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 1)
  const start = inDate < monthStart ? 1 : inDate.getDate()
  const end = outDate >= monthEnd ? days + 1 : outDate.getDate() // the check-out day itself is free
  return { start, end: Math.max(end, start + 1) }
}

export function CalendarPage() {
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const calendar = useQuery({ queryKey: ['admin', 'calendar', monthKey(month)], queryFn: () => adminApi.calendar(monthKey(month)), placeholderData: (previous) => previous })
  const shift = (n: number) => setMonth((m) => new Date(m.getFullYear(), m.getMonth() + n, 1))
  const todayCol = (() => {
    const t = new Date()
    return t.getFullYear() === month.getFullYear() && t.getMonth() === month.getMonth() ? t.getDate() : null
  })()

  return (
    <>
      <PageHeader
        title="Availability calendar"
        subtitle="Every room, every night. Solid bars are confirmed, striped bars are waiting for confirmation."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" small onClick={() => shift(-1)}>
              ←
            </Button>
            <span className="min-w-36 text-center text-sm font-semibold text-slate-900">{month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</span>
            <Button variant="secondary" small onClick={() => shift(1)}>
              →
            </Button>
          </div>
        }
      />
      {calendar.isPending ? (
        <LoadingState />
      ) : calendar.isError ? (
        <ErrorState message={errorMessage(calendar.error)} onRetry={() => calendar.refetch()} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="min-w-[900px]">
            <div className="grid border-b border-slate-200 bg-slate-50 text-center text-[11px] text-slate-500" style={{ gridTemplateColumns: `7rem repeat(${calendar.data.days}, minmax(0, 1fr))` }}>
              <div className="px-3 py-2 text-left font-medium">Room</div>
              {Array.from({ length: calendar.data.days }, (_, i) => (
                <div key={i} className={`py-2 ${todayCol === i + 1 ? 'bg-brand-100 font-bold text-brand-800' : ''}`}>
                  {i + 1}
                </div>
              ))}
            </div>
            {calendar.data.rooms.map((room) => (
              <div key={room.roomId} className="grid items-center border-b border-slate-100 last:border-0" style={{ gridTemplateColumns: `7rem repeat(${calendar.data.days}, minmax(0, 1fr))` }}>
                <div className={`px-3 py-2 text-sm ${room.active ? 'text-slate-900' : 'text-slate-400 line-through'}`}>
                  <span className="font-semibold">{room.number}</span> <span className="text-xs text-slate-500">{statusLabel(room.type)}</span>
                </div>
                <div className="relative col-span-full col-start-2 grid h-9" style={{ gridTemplateColumns: `repeat(${calendar.data.days}, minmax(0, 1fr))`, gridColumn: `2 / span ${calendar.data.days}` }}>
                  {Array.from({ length: calendar.data.days }, (_, i) => (
                    <div key={i} className={`border-l border-slate-100 ${todayCol === i + 1 ? 'bg-brand-50' : ''}`} style={{ gridColumn: i + 1, gridRow: 1 }} />
                  ))}
                  {room.bookings.map((b) => {
                    const { start, end } = columns(b, calendar.data.first, calendar.data.days)
                    const confirmed = b.status === 'CONFIRMED'
                    return (
                      <div
                        key={b.bookingId}
                        title={`${b.number} · ${b.customerName} · ${b.checkIn} to ${b.checkOut} · ${b.status.toLowerCase()}`}
                        className={`z-10 m-1 flex items-center overflow-hidden rounded-md px-1.5 text-[11px] font-medium ${confirmed ? 'bg-brand-600 text-white' : 'border border-dashed border-amber-500 bg-amber-100 text-amber-900'}`}
                        style={{ gridColumn: `${start} / ${end}`, gridRow: 1 }}
                      >
                        <span className="truncate">{b.customerName}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  )
}
