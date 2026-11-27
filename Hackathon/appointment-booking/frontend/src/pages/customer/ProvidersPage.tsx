import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { customerApi } from '../../api/endpoints'
import { ProviderPhoto } from '../../components/ProviderPhoto'
import { formatTime, longDay } from '../../dates'
import { useDebounced } from '../../hooks'
import { EmptyState, ErrorState, Field, LoadingState, PageHeader } from '../../components/ui'

export function ProvidersPage() {
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const list = useQuery({ queryKey: ['providers', q], queryFn: () => customerApi.providers(q), placeholderData: (previous) => previous })

  return (
    <>
      <PageHeader title="Find a provider" subtitle="Choose who you want to see, then pick an open time on their calendar." />
      <div className="mb-4 max-w-md">
        <Field label="Search" placeholder="Name, speciality or place…" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="No provider found" hint="Try a different word, such as a speciality or a place." />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {list.data.map((p) => (
            <li key={p.id}>
              <Link to={`/providers/${p.id}`} className="block h-full rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-brand-300 hover:shadow-md">
                <div className="flex items-center gap-4">
                  <ProviderPhoto id={p.id} name={p.name} title={p.title} className="size-16 text-lg" />
                  <div className="min-w-0">
                    <h2 className="truncate font-semibold text-slate-900">{p.name}</h2>
                    <p className="text-sm font-medium text-brand-700">{p.title}</p>
                    {p.location && <p className="truncate text-xs text-slate-500">{p.location}</p>}
                  </div>
                </div>
                {p.description && <p className="mt-3 line-clamp-2 text-sm text-slate-600">{p.description}</p>}
                <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-sm">
                  {p.nextFree ? (
                    <>
                      <span className="text-slate-600">
                        Next: <span className="font-medium text-slate-900">{longDay(p.nextFree)}, {formatTime(p.nextFree)}</span>
                      </span>
                      <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">{p.freeSlots} open</span>
                    </>
                  ) : (
                    <span className="text-slate-500">No open slots in the next two weeks</span>
                  )}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
