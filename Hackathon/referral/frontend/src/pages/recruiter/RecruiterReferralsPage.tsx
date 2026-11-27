import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { recruiterApi } from '../../api/endpoints'
import type { Referral, ReferralStatus } from '../../api/types'
import { Pipeline } from '../../components/Pipeline'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, formatDate, LoadingState, Modal, PageHeader, statusLabel, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

type Tab = 'ALL' | ReferralStatus
const tabs: Tab[] = ['SUBMITTED', 'SCREENING', 'INTERVIEW', 'SELECTED', 'REJECTED', 'ALL']

export function RecruiterReferralsPage() {
  const [tab, setTab] = useState<Tab>('SUBMITTED')
  const [jobId, setJobId] = useState('')
  const [search, setSearch] = useState('')
  const [moving, setMoving] = useState<{ referral: Referral; to: ReferralStatus } | null>(null)
  const debouncedSearch = useDebounced(search)

  const status = tab === 'ALL' ? undefined : tab
  const list = useQuery({
    queryKey: ['recruiter', 'referrals', status, jobId, debouncedSearch],
    queryFn: () => recruiterApi.referrals({ status, jobId: jobId ? Number(jobId) : undefined, search: debouncedSearch || undefined }),
    placeholderData: (previous) => previous,
  })
  const dashboard = useQuery({ queryKey: ['recruiter', 'dashboard'], queryFn: recruiterApi.dashboard })
  const jobs = useQuery({ queryKey: ['recruiter', 'jobs', 'ALL'], queryFn: () => recruiterApi.jobs() })
  const counts = new Map((dashboard.data?.byStatus ?? []).map((s) => [s.status, s.count]))
  const filtered = jobId !== '' || search !== ''

  return (
    <>
      <PageHeader title="Referrals" subtitle="Move each referral one stage at a time, or reject it." />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label="Filter by stage" className="flex gap-1 overflow-x-auto">
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
              {t === 'ALL' ? 'All' : statusLabel(t)}
              {t !== 'ALL' && counts.has(t) && <span className="ml-1 opacity-70">({counts.get(t)})</span>}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap gap-3">
          <label htmlFor="search" className="sr-only">
            Search referrals
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search candidate, job or referrer"
            className="w-60 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
          <label htmlFor="job" className="sr-only">
            Filter by job
          </label>
          <select id="job" value={jobId} onChange={(e) => setJobId(e.target.value)} className="max-w-56 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">All jobs</option>
            {(jobs.data ?? []).map((j) => (
              <option key={j.id} value={j.id}>
                {j.title}
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
        <EmptyState title={filtered ? 'No referrals match your filters' : tab === 'SUBMITTED' ? 'No new referrals waiting' : 'Nothing to show'} />
      ) : (
        <ul className="space-y-3">
          {list.data.map((r) => {
            const forward = r.allowedNext.find((s) => s !== 'REJECTED')
            return (
              <li key={r.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-900">{r.candidateName}</p>
                    <p className="text-sm text-slate-700">
                      for {r.jobTitle} <span className="text-slate-400">· {r.jobDepartment}</span>
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {r.candidateEmail}
                      {r.candidatePhone && ` · ${r.candidatePhone}`}
                      {r.resumeUrl && (
                        <>
                          {' · '}
                          <a href={r.resumeUrl} target="_blank" rel="noopener noreferrer" className="font-medium text-brand-700 hover:underline">
                            Resume
                          </a>
                        </>
                      )}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      Referred by {r.referrerName} on {formatDate(r.submittedAt)}
                    </p>
                    {r.notes && <p className="mt-2 text-sm text-slate-600">“{r.notes}”</p>}
                    {r.statusNote && <p className="mt-2 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">Note: {r.statusNote}</p>}
                  </div>
                  <div className="flex flex-col items-end gap-3">
                    <Pipeline status={r.status} />
                    {r.allowedNext.length > 0 && (
                      <div className="flex gap-2">
                        {forward && (
                          <Button small onClick={() => setMoving({ referral: r, to: forward })}>
                            Move to {statusLabel(forward).toLowerCase()}
                          </Button>
                        )}
                        {r.allowedNext.includes('REJECTED') && (
                          <Button variant="secondary" small onClick={() => setMoving({ referral: r, to: 'REJECTED' })}>
                            Reject
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {moving && <MoveModal referral={moving.referral} to={moving.to} onClose={() => setMoving(null)} />}
    </>
  )
}

function MoveModal({ referral, to, onClose }: { referral: Referral; to: ReferralStatus; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const reject = to === 'REJECTED'

  const move = useMutation({
    mutationFn: () => recruiterApi.move(referral.id, to, note),
    onSuccess: () => {
      toast.success(`${referral.candidateName}: ${reject ? 'rejected' : `moved to ${statusLabel(to).toLowerCase()}`}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError ? (err.fieldErrors.note ?? err.message) : errorMessage(err))
      if (err instanceof ApiError && err.status === 409) queryClient.invalidateQueries() // someone else already moved it
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    move.mutate()
  }

  return (
    <Modal title={reject ? 'Reject this referral?' : `Move to ${statusLabel(to).toLowerCase()}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-slate-600">
          {referral.candidateName} for {referral.jobTitle}, currently {statusLabel(referral.status).toLowerCase()}.
          {reject && ' Rejected referrals are final.'}
        </p>
        <TextAreaField
          label={reject ? 'Reason (optional)' : 'Note for the referrer (optional)'}
          maxLength={255}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          error={error ?? undefined}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={move.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant={reject ? 'danger' : 'primary'} loading={move.isPending}>
            {reject ? 'Reject' : `Move to ${statusLabel(to).toLowerCase()}`}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
