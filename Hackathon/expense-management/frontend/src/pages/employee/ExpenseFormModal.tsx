import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { expenseApi } from '../../api/endpoints'
import { useToast } from '../../components/Toast'
import { Button, Field, formatMoney, Modal, SelectField, TextAreaField } from '../../components/ui'

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function ExpenseFormModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: expenseApi.categories, staleTime: Infinity })
  const [form, setForm] = useState({ expenseDate: today(), category: '', amount: '', description: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const submit = useMutation({
    mutationFn: () =>
      expenseApi.submit({
        expenseDate: form.expenseDate,
        category: form.category,
        amount: Number(form.amount),
        description: form.description,
      }),
    onSuccess: (expense) => {
      toast.success(`Submitted ${formatMoney(expense.amount)} for review`)
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

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    submit.mutate()
  }

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  return (
    <Modal title="New expense" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Date" type="date" max={today()} value={form.expenseDate} onChange={set('expenseDate')} error={fieldErrors.expenseDate} />
          <Field
            label="Amount (₹)"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            placeholder="0.00"
            value={form.amount}
            onChange={set('amount')}
            error={fieldErrors.amount}
          />
        </div>
        <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
          <option value="">Select a category</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </SelectField>
        <TextAreaField
          label="Description"
          maxLength={500}
          placeholder="What was this for?"
          value={form.description}
          onChange={set('description')}
          error={fieldErrors.description}
          hint={`${form.description.length}/500`}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={submit.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={submit.isPending}>
            Submit expense
          </Button>
        </div>
      </form>
    </Modal>
  )
}
