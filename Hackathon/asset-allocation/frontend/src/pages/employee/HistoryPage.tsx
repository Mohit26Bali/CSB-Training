import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '../../api/client'
import { employeeApi } from '../../api/endpoints'
import { AssetPhoto } from '../../components/AssetPhoto'
import { EmptyState, ErrorState, formatDay, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

export function HistoryPage() {
  const list = useQuery({ queryKey: ['my', 'history'], queryFn: employeeApi.history })
  return (
    <>
      <PageHeader title="History" subtitle="Equipment you have handed back." />
      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title="Nothing returned yet" hint="Equipment you hand back shows up here." />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <AssetPhoto category={a.category} className="hidden w-28 shrink-0 sm:block" />
              <div className="min-w-0 flex-1 space-y-1 p-4">
                <h2 className="font-semibold text-slate-900">
                  {a.assetName} <span className="font-mono text-xs font-normal text-slate-500">{a.assetTag}</span>
                </h2>
                <p className="text-sm text-slate-600">
                  {formatDay(a.assignedAt.slice(0, 10))} to {a.returnedAt && formatDay(a.returnedAt.slice(0, 10))}
                </p>
                <p className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
                  Condition: <StatusBadge status={a.conditionOut} /> out, <StatusBadge status={a.conditionIn ?? a.conditionOut} /> back · checked in by {a.receivedBy}
                </p>
                {a.receiveNote && <p className="text-xs text-slate-500">IT note: {a.receiveNote}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
