import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { requestApi } from '../../api/endpoints'
import { useToast } from '../../components/Toast'
import { Button, Field, formatMoney, Modal, SelectField, TextAreaField } from '../../components/ui'

export function RequestFormModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: requestApi.categories, staleTime: Infinity })
  const [form, setForm] = useState({ itemName: '', category: '', quantity: '1', estimatedUnitCost: '', justification: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const submit = useMutation({
    mutationFn: () =>
      requestApi.submit({
        itemName: form.itemName,
        category: form.category,
        quantity: Number(form.quantity),
        estimatedUnitCost: Number(form.estimatedUnitCost),
        justification: form.justification,
      }),
    onSuccess: (r) => {
      toast.success(`Requested ${r.quantity} × ${r.itemName} (${formatMoney(r.estimatedTotal)})`)
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

  const total = Number(form.quantity) > 0 && Number(form.estimatedUnitCost) > 0 ? Number(form.quantity) * Number(form.estimatedUnitCost) : null

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    submit.mutate()
  }

  return (
    <Modal title="New purchase request" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Item" maxLength={120} placeholder="For example: 24-inch monitor" value={form.itemName} onChange={set('itemName')} error={fieldErrors.itemName} />
        <SelectField label="Category" value={form.category} onChange={set('category')} error={fieldErrors.category}>
          <option value="">Select a category</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </SelectField>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Quantity" type="number" min="1" step="1" value={form.quantity} onChange={set('quantity')} error={fieldErrors.quantity} />
          <Field
            label="Estimated cost per unit (₹)"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            value={form.estimatedUnitCost}
            onChange={set('estimatedUnitCost')}
            error={fieldErrors.estimatedUnitCost}
          />
        </div>
        <p className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-600" aria-live="polite">
          {total === null ? 'Enter a quantity and cost to see the estimated total.' : <>Estimated total: <strong>{formatMoney(total)}</strong></>}
        </p>
        <TextAreaField
          label="Justification"
          maxLength={500}
          placeholder="Why does the team need this?"
          value={form.justification}
          onChange={set('justification')}
          error={fieldErrors.justification}
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
