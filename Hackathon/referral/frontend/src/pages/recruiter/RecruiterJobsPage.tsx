import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { recruiterApi } from '../../api/endpoints'
import type { Job, JobStatus } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, statusLabel, Tag, TextAreaField } from '../../components/ui'

type Tab = 'ALL' | JobStatus
const tabs: Tab[] = ['ALL', 'DRAFT', 'PUBLISHED', 'CLOSED']

export function RecruiterJobsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('ALL')
  const [editing, setEditing] = useState<Job | 'new' | null>(null)

  const jobs = useQuery({
    queryKey: ['recruiter', 'jobs', tab],
    queryFn: () => recruiterApi.jobs(tab === 'ALL' ? undefined : tab),
    placeholderData: (previous) => previous,
  })

  const change = useMutation({
    mutationFn: ({ kind, job }: { kind: 'publish' | 'close'; job: Job }) =>
      kind === 'publish' ? recruiterApi.publish(job.id) : recruiterApi.close(job.id),
    onSuccess: (j) => toast.success(j.status === 'PUBLISHED' ? `“${j.title}” is now open for referrals` : `“${j.title}” is closed`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })
  const busyId = change.isPending ? change.variables?.job.id : undefined

  return (
    <>
      <PageHeader title="Jobs" subtitle="Draft a job, publish it to start receiving referrals, close it when it is filled." actions={<Button onClick={() => setEditing('new')}>New job</Button>} />

      <div role="tablist" aria-label="Filter by status" className="mb-4 flex gap-1 overflow-x-auto">
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
          </button>
        ))}
      </div>

      {jobs.isPending ? (
        <LoadingState />
      ) : jobs.isError ? (
        <ErrorState message={errorMessage(jobs.error)} onRetry={() => jobs.refetch()} />
      ) : jobs.data.length === 0 ? (
        <EmptyState title="No jobs here" action={tab === 'ALL' && <Button onClick={() => setEditing('new')}>Create the first job</Button>} />
      ) : (
        <ul className="space-y-3">
          {jobs.data.map((job) => (
            <li key={job.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{job.title}</h2>
                    <StatusBadge status={job.status} />
                  </div>
                  <p className="mt-0.5 text-sm text-slate-500">
                    {job.department} · {job.location} · {job.referralCount} referral{job.referralCount === 1 ? '' : 's'}
                  </p>
                  <p className="mt-2 line-clamp-2 text-sm text-slate-600">{job.description}</p>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {job.skills.map((s) => (
                      <Tag key={s}>{s}</Tag>
                    ))}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" small onClick={() => setEditing(job)}>
                    Edit
                  </Button>
                  {job.status !== 'PUBLISHED' && (
                    <Button small loading={busyId === job.id} disabled={change.isPending} onClick={() => change.mutate({ kind: 'publish', job })}>
                      {job.status === 'CLOSED' ? 'Reopen' : 'Publish'}
                    </Button>
                  )}
                  {job.status === 'PUBLISHED' && (
                    <Button variant="secondary" small loading={busyId === job.id} disabled={change.isPending} onClick={() => change.mutate({ kind: 'close', job })}>
                      Close
                    </Button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {editing && <JobFormModal job={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function JobFormModal({ job, onClose }: { job: Job | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    title: job?.title ?? '',
    department: job?.department ?? '',
    location: job?.location ?? '',
    description: job?.description ?? '',
    skills: job?.skills.join(', ') ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const input = { ...form, skills: form.skills.split(',').map((s) => s.trim()).filter(Boolean) }
      return job ? recruiterApi.updateJob(job.id, input) : recruiterApi.createJob(input)
    },
    onSuccess: (saved) => {
      toast.success(job ? `Saved “${saved.title}”` : `Created “${saved.title}” as a draft`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        // the server reports list problems as "skills[0]"; show them under the one skills field
        const mapped: Record<string, string> = {}
        for (const [k, v] of Object.entries(err.fieldErrors)) mapped[k.startsWith('skills') ? 'skills' : k] = v
        setFieldErrors(mapped)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={job ? 'Edit job' : 'New job'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Title" maxLength={120} value={form.title} onChange={set('title')} error={fieldErrors.title} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Department" maxLength={80} value={form.department} onChange={set('department')} error={fieldErrors.department} />
          <Field label="Location" maxLength={80} value={form.location} onChange={set('location')} error={fieldErrors.location} />
        </div>
        <TextAreaField label="Description" rows={4} maxLength={2000} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <Field label="Skills" placeholder="Java, Spring Boot, MySQL" value={form.skills} onChange={set('skills')} error={fieldErrors.skills} hint="Separate skills with commas" />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {job ? 'Save changes' : 'Create draft'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
