import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Application, ApplicationStatus } from '../../api/types'
import { ScoreBadge } from '../../components/ScoreBadge'
import { useToast } from '../../components/Toast'
import {
  Button,
  EmptyState,
  ErrorState,
  formatDate,
  formatMoney,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  statusLabel,
  TextAreaField,
} from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = ApplicationStatus | 'ALL'
const tabs: Tab[] = ['SUBMITTED', 'APPROVED', 'REJECTED', 'ALL']

export function ApplicationsPage() {
  const [tab, setTab] = useState<Tab>('SUBMITTED')
  const [programId, setProgramId] = useState('')
  const [search, setSearch] = useState('')
  const [deciding, setDeciding] = useState<{ application: Application; approve: boolean } | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const list = useQuery({
    queryKey: ['admin', 'applications', status, programId, debouncedSearch],
    queryFn: () =>
      adminApi.applications({ status, programId: programId ? Number(programId) : undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const programs = useQuery({ queryKey: ['admin', 'programs'], queryFn: adminApi.programs })
  const filtered = programId !== '' || search !== ''

  return (
    <>
      <PageHeader title="Applications" subtitle="Approve or reject. Every decision needs a reason the student can read." />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
          {tabs.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                tab === t ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {t === 'ALL' ? 'All' : t === 'SUBMITTED' ? 'Awaiting decision' : statusLabel(t)}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <label htmlFor="search" className="sr-only">
            Search applications
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search student or course"
            className="w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
          <label htmlFor="program" className="sr-only">
            Filter by scholarship
          </label>
          <select
            id="program"
            value={programId}
            onChange={(e) => setProgramId(e.target.value)}
            className="max-w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All scholarships</option>
            {(programs.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState title={filtered ? 'No applications match your filters' : tab === 'SUBMITTED' ? 'Nothing waiting for a decision' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((a) => (
            <li key={a.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">
                    {a.studentName} <span className="font-normal text-slate-500">· {a.studentUsername}</span>
                  </p>
                  <p className="text-sm text-slate-700">
                    {a.programName} <span className="text-slate-500">({formatMoney(a.programAmount)})</span>
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    {a.course}, year {a.yearOfStudy} · CGPA {a.gpa.toFixed(2)} · family income {formatMoney(a.annualFamilyIncome)}
                  </p>
                  <div className="mt-2">
                    <ScoreBadge score={a.eligibilityScore} meets={a.meetsCriteria} />
                  </div>
                  <p className="mt-2 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">“{a.statement}”</p>
                  <p className="mt-2 text-xs text-slate-500">Submitted {formatDate(a.submittedAt)}</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge status={a.status} />
                  {a.status === 'SUBMITTED' && (
                    <>
                      <Button small onClick={() => setDeciding({ application: a, approve: true })}>
                        Approve
                      </Button>
                      <Button variant="secondary" small onClick={() => setDeciding({ application: a, approve: false })}>
                        Reject
                      </Button>
                    </>
                  )}
                </div>
              </div>
              {a.decisionReason && (
                <p className="mt-3 text-xs text-slate-500">
                  {a.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {a.decidedBy} on {formatDate(a.decidedAt)}:{' '}
                  <span className="text-slate-700">{a.decisionReason}</span>
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {deciding && <DecisionModal application={deciding.application} approve={deciding.approve} onClose={() => setDeciding(null)} />}
    </>
  )
}

function DecisionModal({ application, approve, onClose }: { application: Application; approve: boolean; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const decide = useMutation({
    mutationFn: () => (approve ? adminApi.approve(application.id, reason) : adminApi.reject(application.id, reason)),
    onSuccess: () => {
      toast.success(`${approve ? 'Approved' : 'Rejected'} ${application.studentName}’s application`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.reason ?? err.message) : errorMessage(err))
      if (err instanceof ApiError && err.status === 409) queryClient.invalidateQueries()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    decide.mutate()
  }

  return (
    <Modal title={approve ? 'Approve this application?' : 'Reject this application?'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {application.studentName} applied for <strong>{application.programName}</strong>.{' '}
          {!application.meetsCriteria && approve && 'Note: this application does not meet the criteria. '}
          The student will read your reason.
        </p>
        <TextAreaField
          label="Reason"
          maxLength={500}
          placeholder={approve ? 'For example: strong record and clear financial need' : 'For example: CGPA is below the minimum'}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          error={error ?? undefined}
          hint={`${reason.length}/500`}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={decide.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant={approve ? 'primary' : 'danger'} loading={decide.isPending}>
            {approve ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
