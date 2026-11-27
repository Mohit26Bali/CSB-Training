import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { enrollmentApi } from '../../api/endpoints'
import type { Enrollment, EnrollmentStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import {
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  formatDate,
  LoadingState,
  PageHeader,
  StatusBadge,
} from '../../components/ui'

type Tab = 'ALL' | EnrollmentStatus

const tabs: { value: Tab; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
]

export function MyEnrollmentsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ALL')
  const [toCancel, setToCancel] = useState<Enrollment | null>(null)

  const mine = useQuery({ queryKey: ['my-enrollments'], queryFn: enrollmentApi.mine })

  const cancel = useMutation({
    mutationFn: (id: number) => enrollmentApi.cancel(id),
    onSuccess: (result) => toast.success(result.message),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setToCancel(null)
      return queryClient.invalidateQueries()
    },
  })

  const all = mine.data ?? []
  const count = (status: Tab) => (status === 'ALL' ? all.length : all.filter((e) => e.status === status).length)
  const visible = tab === 'ALL' ? all : all.filter((e) => e.status === tab)

  return (
    <>
      <PageHeader title="My enrollments" subtitle="Track the courses you have requested." />

      {mine.isPending ? (
        <LoadingState />
      ) : mine.isError ? (
        <ErrorState message={errorMessage(mine.error)} onRetry={() => mine.refetch()} />
      ) : all.length === 0 ? (
        <EmptyState
          title="You haven’t requested any courses yet"
          action={
            <Link to="/courses" className="text-sm font-medium text-brand-700 hover:underline">
              Browse courses
            </Link>
          }
        />
      ) : (
        <>
          <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
            {tabs.map((t) => (
              <button
                key={t.value}
                role="tab"
                aria-selected={tab === t.value}
                onClick={() => setTab(t.value)}
                className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                  tab === t.value ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {t.label} <span className="opacity-70">({count(t.value)})</span>
              </button>
            ))}
          </div>

          {visible.length === 0 ? (
            <EmptyState title={`No ${tab.toLowerCase()} requests`} />
          ) : (
            <ul className="space-y-3">
              {visible.map((e) => (
                <li
                  key={e.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{e.courseTitle}</p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      Requested {formatDate(e.requestedAt)}
                      {e.decidedAt && ` · Decided ${formatDate(e.decidedAt)}`}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusBadge status={e.status} />
                    {e.status === 'PENDING' && (
                      <Button variant="secondary" small onClick={() => setToCancel(e)}>
                        Cancel request
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {toCancel && (
        <ConfirmDialog
          title="Cancel this request?"
          message={`Your request for “${toCancel.courseTitle}” will be withdrawn. You can request it again later.`}
          confirmLabel="Cancel request"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}
