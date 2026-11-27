import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { employeeApi } from '../../api/endpoints'
import { floorLabel, SlotMap } from '../../components/SlotMap'
import { Button, EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/ui'

export function SlotsPage() {
  const [floor, setFloor] = useState<number | null>(null)
  const floors = useQuery({ queryKey: ['floors'], queryFn: employeeApi.floors })
  const slots = useQuery({ queryKey: ['slots', floor], queryFn: () => employeeApi.freeSlots(floor ?? undefined), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader
        title="Free slots"
        subtitle="Every slot listed here is free right now. The admin picks yours when your request is approved."
        actions={
          <Link to="/my-parking">
            <Button>Request a slot</Button>
          </Link>
        }
      />
      <div role="tablist" aria-label="Filter by floor" className="mb-6 flex gap-1 overflow-x-auto">
        {[null, ...(floors.data ?? [])].map((f) => (
          <button
            key={f ?? 'all'}
            role="tab"
            aria-selected={floor === f}
            onClick={() => setFloor(f)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${floor === f ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {f === null ? 'All floors' : floorLabel(f)}
          </button>
        ))}
      </div>
      {slots.isPending ? (
        <LoadingState />
      ) : slots.isError ? (
        <ErrorState message={errorMessage(slots.error)} onRetry={() => slots.refetch()} />
      ) : slots.data.length === 0 ? (
        <EmptyState title="No free slots here" hint="You can still send a request; it goes into the queue." />
      ) : (
        <>
          <p className="mb-4 text-sm text-slate-600">
            {slots.data.length} free slot{slots.data.length === 1 ? '' : 's'}
          </p>
          <SlotMap slots={slots.data} />
        </>
      )}
    </>
  )
}
