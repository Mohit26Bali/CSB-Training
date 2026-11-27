import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Program } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, formatMoney, LoadingState, Modal, PageHeader, Tag, TextAreaField } from '../../components/ui'

export function ProgramsAdminPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Program | 'new' | null>(null)
  const programs = useQuery({ queryKey: ['admin', 'programs'], queryFn: adminApi.programs })

  const toggle = useMutation({
    mutationFn: (p: Program) =>
      adminApi.updateProgram(p.id, {
        name: p.name,
        description: p.description ?? '',
        amount: p.amount,
        deadline: p.deadline,
        minGpa: p.minGpa,
        maxFamilyIncome: p.maxFamilyIncome,
        active: !p.active,
      }),
    onSuccess: (p) => toast.success(p.active ? `“${p.name}” is visible to students again` : `“${p.name}” is now hidden from students`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => queryClient.invalidateQueries(),
  })

  return (
    <>
      <PageHeader title="Scholarships" subtitle="Create and manage the scholarships students can apply for." actions={<Button onClick={() => setEditing('new')}>New scholarship</Button>} />

      {programs.isPending ? (
        <LoadingState />
      ) : programs.isError ? (
        <ErrorState message={errorMessage(programs.error)} onRetry={() => programs.refetch()} />
      ) : programs.data.length === 0 ? (
        <EmptyState title="No scholarships yet" action={<Button onClick={() => setEditing('new')}>Create the first one</Button>} />
      ) : (
        <ul className="space-y-3">
          {programs.data.map((p) => (
            <li key={p.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{p.name}</h2>
                    {!p.active ? <Tag>Hidden</Tag> : p.open ? <Tag tone="green">Open</Tag> : <Tag>Closed</Tag>}
                  </div>
                  <p className="mt-1 text-sm text-slate-600">{p.description ?? 'No description.'}</p>
                  <p className="mt-2 text-xs text-slate-500">
                    {formatMoney(p.amount)} · deadline {p.deadline} · CGPA ≥ {p.minGpa.toFixed(2)} · income ≤ {formatMoney(p.maxFamilyIncome)} ·{' '}
                    {p.applicationCount} application{p.applicationCount === 1 ? '' : 's'} ({p.approvedCount} approved)
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button variant="secondary" small onClick={() => setEditing(p)}>
                    Edit
                  </Button>
                  <Button variant="secondary" small loading={toggle.isPending && toggle.variables?.id === p.id} onClick={() => toggle.mutate(p)}>
                    {p.active ? 'Hide' : 'Show'}
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {editing && <ProgramFormModal program={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function ProgramFormModal({ program, onClose }: { program: Program | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: program?.name ?? '',
    description: program?.description ?? '',
    amount: String(program?.amount ?? ''),
    deadline: program?.deadline ?? '',
    minGpa: String(program?.minGpa ?? ''),
    maxFamilyIncome: String(program?.maxFamilyIncome ?? ''),
    active: program?.active ?? true,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const input = {
        name: form.name,
        description: form.description,
        amount: Number(form.amount),
        deadline: form.deadline,
        minGpa: Number(form.minGpa),
        maxFamilyIncome: Number(form.maxFamilyIncome),
        active: form.active,
      }
      return program ? adminApi.updateProgram(program.id, input) : adminApi.createProgram(input)
    },
    onSuccess: (saved) => {
      toast.success(program ? `Saved “${saved.name}”` : `Created “${saved.name}”`)
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
    <Modal title={program ? 'Edit scholarship' : 'New scholarship'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Name" maxLength={120} value={form.name} onChange={set('name')} error={fieldErrors.name} />
        <TextAreaField label="Description" maxLength={500} value={form.description} onChange={set('description')} error={fieldErrors.description} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Award (₹)" type="number" min="1" step="1000" value={form.amount} onChange={set('amount')} error={fieldErrors.amount} />
          <Field label="Deadline" type="date" value={form.deadline} onChange={set('deadline')} error={fieldErrors.deadline} hint="Applications are accepted up to and including this day" />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Minimum CGPA" type="number" min="0" max="10" step="0.01" value={form.minGpa} onChange={set('minGpa')} error={fieldErrors.minGpa} />
          <Field label="Max family income (₹)" type="number" min="1" step="1000" value={form.maxFamilyIncome} onChange={set('maxFamilyIncome')} error={fieldErrors.maxFamilyIncome} />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} className="size-4 rounded border-slate-300" />
          Visible to students
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {program ? 'Save changes' : 'Create scholarship'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
