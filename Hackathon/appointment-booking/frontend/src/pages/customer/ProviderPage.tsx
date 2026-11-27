import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import type { Slot } from '../../api/types'
import { ProviderPhoto } from '../../components/ProviderPhoto'
import { useToast } from '../../components/Toast'
import { addDays, formatTime, isoDay, longDay, parseDay, weekday } from '../../dates'
import { Button, EmptyState, ErrorState, LoadingState, Modal, TextAreaField } from '../../components/ui'

const WINDOW = 7

export function ProviderPage() {
  const id = Number(useParams().id)
  const today = useMemo(() => isoDay(new Date()), [])
  const [from, setFrom] = useState(today)
  const [day, setDay] = useState<string | null>(null)
  const [picked, setPicked] = useState<Slot | null>(null)

  const providers = useQuery({ queryKey: ['providers', ''], queryFn: () => customerApi.providers() })
  const provider = providers.data?.find((p) => p.id === id)
  const slots = useQuery({ queryKey: ['slots', id, from], queryFn: () => customerApi.slots(id, from, WINDOW), placeholderData: (previous) => previous })

  const dates = Array.from({ length: WINDOW }, (_, i) => addDays(from, i))
  const byDay = (d: string) => (slots.data ?? []).filter((s) => s.start.slice(0, 10) === d)
  const selected = day && dates.includes(day) ? day : (dates.find((d) => byDay(d).some((s) => s.status === 'AVAILABLE')) ?? dates[0])

  if (providers.isError) return <ErrorState message={errorMessage(providers.error)} onRetry={() => providers.refetch()} />
  if (!providers.isPending && !provider) {
    return <EmptyState title="That provider does not exist" action={<Link to="/providers" className="font-medium text-brand-700 hover:underline">Back to providers</Link>} />
  }

  return (
    <>
      <Link to="/providers" className="text-sm font-medium text-brand-700 hover:underline">
        ← All providers
      </Link>
      {provider && (
        <header className="mt-3 flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <ProviderPhoto id={provider.id} name={provider.name} title={provider.title} className="size-20 text-2xl" />
          <div className="min-w-0">
            <h1 className="text-xl font-semibold text-slate-900">{provider.name}</h1>
            <p className="font-medium text-brand-700">{provider.title}</p>
            {provider.location && <p className="text-sm text-slate-500">{provider.location}</p>}
            {provider.description && <p className="mt-1 text-sm text-slate-600">{provider.description}</p>}
          </div>
        </header>
      )}

      <section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="font-semibold text-slate-900">Pick a time</h2>
          <div className="flex gap-2">
            <Button variant="secondary" small disabled={from <= today} onClick={() => setFrom(addDays(from, -WINDOW) < today ? today : addDays(from, -WINDOW))}>
              ← Earlier
            </Button>
            <Button variant="secondary" small onClick={() => setFrom(addDays(from, WINDOW))}>
              Later →
            </Button>
          </div>
        </div>

        <ul className="grid grid-cols-7 gap-1.5" role="tablist" aria-label="Days">
          {dates.map((d) => {
            const open = byDay(d).filter((s) => s.status === 'AVAILABLE').length
            return (
              <li key={d}>
                <button
                  role="tab"
                  aria-selected={selected === d}
                  onClick={() => setDay(d)}
                  className={`w-full rounded-lg border px-1 py-2 text-center transition ${selected === d ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 hover:border-brand-400'}`}
                >
                  <span className={`block text-[11px] uppercase ${selected === d ? 'text-white/80' : 'text-slate-500'}`}>{weekday(d)}</span>
                  <span className="block text-lg font-semibold">{parseDay(d).getDate()}</span>
                  <span className={`block text-[11px] font-semibold ${selected === d ? 'text-white' : open > 0 ? 'text-emerald-600' : 'text-slate-400'}`}>{open > 0 ? `${open} open` : '–'}</span>
                </button>
              </li>
            )
          })}
        </ul>

        <div className="mt-5">
          <h3 className="mb-2 text-sm font-medium text-slate-700">{longDay(selected)}</h3>
          {slots.isPending ? (
            <LoadingState />
          ) : slots.isError ? (
            <ErrorState message={errorMessage(slots.error)} onRetry={() => slots.refetch()} />
          ) : byDay(selected).length === 0 ? (
            <p className="text-sm text-slate-500">No slots on this day. Try another day.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {byDay(selected).map((s) => (
                <li key={s.id}>
                  <button
                    disabled={s.status !== 'AVAILABLE'}
                    onClick={() => setPicked(s)}
                    title={s.status === 'AVAILABLE' ? 'Book this time' : 'Already taken'}
                    className={`rounded-lg border px-3 py-2 text-sm font-medium transition ${
                      s.status === 'AVAILABLE' ? 'border-brand-300 bg-brand-50 text-brand-800 hover:bg-brand-600 hover:text-white' : 'cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400 line-through'
                    }`}
                  >
                    {formatTime(s.start)}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
      {picked && provider && <BookModal slot={picked} providerName={provider.name} onClose={() => setPicked(null)} />}
    </>
  )
}

function BookModal({ slot, providerName, onClose }: { slot: Slot; providerName: string; onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const book = useMutation({
    mutationFn: () => customerApi.book(slot.id, reason),
    onSuccess: (a) => {
      toast.success(`${a.number} requested for ${longDay(a.start)}, ${formatTime(a.start)}`)
      queryClient.invalidateQueries()
      navigate('/appointments')
    },
    onError: (err) => {
      setError(err instanceof ApiError && err.fieldErrors.slotId ? err.fieldErrors.slotId : errorMessage(err))
      queryClient.invalidateQueries({ queryKey: ['slots'] })
    },
  })

  return (
    <Modal title="Confirm your booking" onClose={onClose}>
      <div className="space-y-4">
        <p className="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-900">
          <span className="font-semibold">{providerName}</span>
          <br />
          {longDay(slot.start)}, {formatTime(slot.start)} to {formatTime(slot.end)} ({slot.minutes} minutes)
        </p>
        {error && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        )}
        <TextAreaField label="What is it about? (optional)" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />
        <p className="text-xs text-slate-500">The provider confirms your request. You can cancel up to two hours before it starts.</p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={book.isPending}>
            Back
          </Button>
          <Button loading={book.isPending} onClick={() => book.mutate()}>
            Book this time
          </Button>
        </div>
      </div>
    </Modal>
  )
}
