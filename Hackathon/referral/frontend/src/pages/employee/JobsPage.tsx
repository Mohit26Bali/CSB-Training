import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { jobApi, referralApi } from '../../api/endpoints'
import type { Job } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, Tag, TextAreaField } from '../../components/ui'
import { useDebounced } from '../../hooks'

export function JobsPage() {
  const [search, setSearch] = useState('')
  const [skill, setSkill] = useState('')
  const [referring, setReferring] = useState<Job | null>(null)
  const debouncedSearch = useDebounced(search)

  const jobs = useQuery({
    queryKey: ['jobs', debouncedSearch, skill],
    queryFn: () => jobApi.open(debouncedSearch || undefined, skill || undefined),
    placeholderData: (previous) => previous,
  })
  const skills = useQuery({ queryKey: ['skills'], queryFn: jobApi.skills })
  const mine = useQuery({ queryKey: ['referrals', 'mine', 'all'], queryFn: () => referralApi.mine() })

  const referredCount = new Map<number, number>()
  for (const r of mine.data ?? []) referredCount.set(r.jobId, (referredCount.get(r.jobId) ?? 0) + 1)
  const filtered = search !== '' || skill !== ''

  return (
    <>
      <PageHeader title="Open positions" subtitle="Know someone who would be a great fit? Refer them." />

      <div className="mb-6 flex flex-wrap gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="search" className="sr-only">
            Search jobs
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title, team, location or skill"
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="skill" className="sr-only">
            Filter by skill
          </label>
          <select id="skill" value={skill} onChange={(e) => setSkill(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
            <option value="">Any skill</option>
            {(skills.data ?? []).map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        {filtered && (
          <Button
            variant="ghost"
            onClick={() => {
              setSearch('')
              setSkill('')
            }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {jobs.isPending ? (
        <LoadingState />
      ) : jobs.isError ? (
        <ErrorState message={errorMessage(jobs.error)} onRetry={() => jobs.refetch()} />
      ) : jobs.data.length === 0 ? (
        <EmptyState title={filtered ? 'No positions match your filters' : 'There are no open positions right now'} hint={filtered ? 'Try a different search or skill.' : 'Check back soon.'} />
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {jobs.data.map((job) => {
            const count = referredCount.get(job.id) ?? 0
            return (
              <li key={job.id} className="flex flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <h2 className="font-semibold text-slate-900">{job.title}</h2>
                <p className="mt-0.5 text-sm text-slate-500">
                  {job.department} · {job.location}
                </p>
                <p className="mt-3 flex-1 text-sm text-slate-600">{job.description}</p>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {job.skills.map((s) => (
                    <Tag key={s} tone="green">
                      {s}
                    </Tag>
                  ))}
                </div>
                <div className="mt-4 flex items-center justify-between gap-3">
                  {count > 0 ? (
                    <Link to="/referrals" className="text-sm font-medium text-brand-700 hover:underline">
                      You referred {count} candidate{count === 1 ? '' : 's'}
                    </Link>
                  ) : (
                    <span className="text-xs text-slate-500">No referrals from you yet</span>
                  )}
                  <Button small onClick={() => setReferring(job)}>
                    Refer a candidate
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {referring && <ReferModal job={referring} onClose={() => setReferring(null)} />}
    </>
  )
}

function ReferModal({ job, onClose }: { job: Job; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ candidateName: '', candidateEmail: '', candidatePhone: '', resumeUrl: '', notes: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const refer = useMutation({
    mutationFn: () => referralApi.refer({ jobId: job.id, ...form }),
    onSuccess: (r) => {
      toast.success(`Referred ${r.candidateName} for ${job.title}`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
      queryClient.invalidateQueries() // the job may have just been closed
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    refer.mutate()
  }

  return (
    <Modal title={`Refer a candidate for ${job.title}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Candidate name" maxLength={100} value={form.candidateName} onChange={set('candidateName')} error={fieldErrors.candidateName} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Email" type="email" value={form.candidateEmail} onChange={set('candidateEmail')} error={fieldErrors.candidateEmail} />
          <Field label="Phone (optional)" type="tel" value={form.candidatePhone} onChange={set('candidatePhone')} error={fieldErrors.candidatePhone} />
        </div>
        <Field label="Resume link (optional)" placeholder="https://…" value={form.resumeUrl} onChange={set('resumeUrl')} error={fieldErrors.resumeUrl} />
        <TextAreaField
          label="Why would they be a great fit? (optional)"
          maxLength={500}
          value={form.notes}
          onChange={set('notes')}
          error={fieldErrors.notes}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={refer.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={refer.isPending}>
            Submit referral
          </Button>
        </div>
      </form>
    </Modal>
  )
}
