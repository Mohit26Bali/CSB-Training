import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Course, Enrollment, EnrollmentStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, LoadingState, PageHeader, StatusBadge } from '../../components/ui'

type Filter = EnrollmentStatus | 'ALL'

const filters: { value: Filter; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
]

export function RequestsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<Filter>('PENDING')
  const [toReject, setToReject] = useState<Enrollment | null>(null)

  const requests = useQuery({
    queryKey: ['admin', 'enrollments', filter],
    queryFn: () => adminApi.enrollments(filter === 'ALL' ? undefined : filter),
  })
  // Needed to show seats left beside each request and to stop approvals into full or closed courses.
  const courses = useQuery({ queryKey: ['admin', 'courses', '', ''], queryFn: () => adminApi.courses({}) })
  const courseById = new Map<number, Course>((courses.data ?? []).map((c) => [c.id, c]))

  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: number; approve: boolean }) => (approve ? adminApi.approve(id) : adminApi.reject(id)),
    onSuccess: (e) =>
      toast.success(`${e.employeeName}’s request for “${e.courseTitle}” was ${e.status === 'APPROVED' ? 'approved' : 'rejected'}`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setToReject(null)
      return queryClient.invalidateQueries()
    },
  })

  const busyId = decide.isPending ? decide.variables?.id : undefined

  return (
    <>
      <PageHeader title="Enrollment requests" subtitle="Approve or reject employee requests. Approvals use up seats." />

      <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
        {filters.map((f) => (
          <button
            key={f.value}
            role="tab"
            aria-selected={filter === f.value}
            onClick={() => setFilter(f.value)}
            className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
              filter === f.value ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {requests.isPending ? (
        <LoadingState />
      ) : requests.isError ? (
        <ErrorState message={errorMessage(requests.error)} onRetry={() => requests.refetch()} />
      ) : requests.data.length === 0 ? (
        <EmptyState
          title={filter === 'PENDING' ? 'No requests waiting' : 'Nothing to show'}
          hint={filter === 'PENDING' ? 'New employee requests will appear here.' : undefined}
        />
      ) : (
        <ul className="space-y-3">
          {requests.data.map((e) => {
            const course = courseById.get(e.courseId)
            const full = course ? course.approvedCount >= course.capacity : false
            const closed = course ? !course.active : false
            const blocked = full ? 'Course is full' : closed ? 'Course is closed' : null
            return (
              <li
                key={e.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium text-slate-900">
                    {e.employeeName} <span className="font-normal text-slate-500">· {e.employeeUsername}</span>
                  </p>
                  <p className="text-sm text-slate-600">{e.courseTitle}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Requested {formatDate(e.requestedAt)}
                    {e.decidedBy && ` · ${e.status === 'APPROVED' ? 'Approved' : 'Rejected'} by ${e.decidedBy} on ${formatDate(e.decidedAt)}`}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  {course && e.status === 'PENDING' && (
                    <span className={`text-xs ${blocked ? 'font-medium text-rose-700' : 'text-slate-500'}`}>
                      {blocked ?? `${course.capacity - course.approvedCount} of ${course.capacity} seats left`}
                    </span>
                  )}
                  <StatusBadge status={e.status} />
                  {e.status === 'PENDING' && (
                    <>
                      <Button
                        small
                        disabled={blocked !== null}
                        loading={busyId === e.id && decide.variables?.approve === true}
                        onClick={() => decide.mutate({ id: e.id, approve: true })}
                      >
                        Approve
                      </Button>
                      <Button variant="secondary" small disabled={busyId === e.id} onClick={() => setToReject(e)}>
                        Reject
                      </Button>
                    </>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {toReject && (
        <ConfirmDialog
          title="Reject this request?"
          message={`${toReject.employeeName}’s request for “${toReject.courseTitle}” will be rejected. Rejected requests are final.`}
          confirmLabel="Reject request"
          danger
          loading={decide.isPending}
          onConfirm={() => decide.mutate({ id: toReject.id, approve: false })}
          onCancel={() => setToReject(null)}
        />
      )}
    </>
  )
}
