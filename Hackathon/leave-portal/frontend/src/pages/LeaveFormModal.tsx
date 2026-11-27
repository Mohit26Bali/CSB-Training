import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../api/client'
import { leaveApi } from '../api/endpoints'
import { useToast } from '../components/Toast'
import { Button, Field, Modal, SelectField, TextAreaField } from '../components/ui'
import { workingDays } from '../dates'

export function LeaveFormModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const types = useQuery({ queryKey: ['leave', 'types'], queryFn: leaveApi.types, staleTime: Infinity })
  const balance = useQuery({ queryKey: ['leave', 'balance'], queryFn: leaveApi.balance })
  const [form, setForm] = useState({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const submit = useMutation({
    mutationFn: () => leaveApi.submit(form),
    onSuccess: (leave) => {
      toast.success(`Requested ${leave.days} day${leave.days === 1 ? '' : 's'} of ${leave.leaveTypeLabel.toLowerCase()}`)
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

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const days = form.startDate && form.endDate && form.endDate >= form.startDate ? workingDays(form.startDate, form.endDate) : null
  const selected = balance.data?.find((b) => b.leaveType === form.leaveType)

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    submit.mutate()
  }

  return (
    <Modal title="Request leave" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <SelectField label="Leave type" value={form.leaveType} onChange={set('leaveType')} error={fieldErrors.leaveType}>
          {(types.data ?? []).map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </SelectField>
        <div className="grid grid-cols-2 gap-4">
          <Field label="From" type="date" value={form.startDate} onChange={set('startDate')} error={fieldErrors.startDate} />
          <Field label="To" type="date" min={form.startDate || undefined} value={form.endDate} onChange={set('endDate')} error={fieldErrors.endDate} />
        </div>
        <p className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-600" aria-live="polite">
          {days === null
            ? 'Pick your dates to see how many working days this uses.'
            : `${days} working day${days === 1 ? '' : 's'} (weekends are not counted).`}
          {selected?.tracked && <> You have {selected.available} day{selected.available === 1 ? '' : 's'} available.</>}
        </p>
        <TextAreaField
          label="Reason"
          maxLength={500}
          placeholder="A short reason for your manager"
          value={form.reason}
          onChange={set('reason')}
          error={fieldErrors.reason}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={submit.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={submit.isPending}>
            Submit request
          </Button>
        </div>
      </form>
    </Modal>
  )
}
