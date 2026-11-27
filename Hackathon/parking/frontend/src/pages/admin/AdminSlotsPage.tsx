import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Slot, SlotInput } from '../../api/types'
import { SlotMap } from '../../components/SlotMap'
import { useToast } from '../../components/Toast'
import { Button, EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, SelectField } from '../../components/ui'

export function AdminSlotsPage() {
  const [editing, setEditing] = useState<Slot | 'new' | null>(null)
  const slots = useQuery({ queryKey: ['admin', 'slots', 'all'], queryFn: () => adminApi.slots() })

  return (
    <>
      <PageHeader title="Slot map" subtitle="The whole lot. Click a slot to rename it or mark it reserved or closed." actions={<Button onClick={() => setEditing('new')}>New slot</Button>} />
      {slots.isPending ? (
        <LoadingState />
      ) : slots.isError ? (
        <ErrorState message={errorMessage(slots.error)} onRetry={() => slots.refetch()} />
      ) : slots.data.length === 0 ? (
        <EmptyState title="No slots yet" action={<Button onClick={() => setEditing('new')}>Add the first slot</Button>} />
      ) : (
        <SlotMap slots={slots.data} onPick={setEditing} />
      )}
      {editing && <SlotFormModal slot={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function SlotFormModal({ slot, onClose }: { slot: Slot | null; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SlotInput>({ code: slot?.code ?? '', floor: slot?.floor ?? '', status: slot?.status ?? 'AVAILABLE' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => (slot ? adminApi.updateSlot(slot.id, form) : adminApi.createSlot(form)),
    onSuccess: (saved) => {
      toast.success(slot ? `Saved slot ${saved.code}` : `Added slot ${saved.code}`)
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
  const set = (key: keyof SlotInput) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate()
  }

  return (
    <Modal title={slot ? `Slot ${slot.code}` : 'New slot'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {slot?.occupied && (
          <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            In use by {slot.occupantName} ({slot.occupantVehicle}). Release it from Allocations before reserving or closing it.
          </p>
        )}
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Slot code" placeholder="F1-09" maxLength={20} value={form.code} onChange={set('code')} error={fieldErrors.code} />
          <Field label="Floor (0 = ground)" type="number" min={0} max={9} value={form.floor} onChange={set('floor')} error={fieldErrors.floor} />
        </div>
        <SelectField label="Status" value={form.status} onChange={set('status')} error={fieldErrors.status}>
          <option value="AVAILABLE">Available</option>
          <option value="RESERVED">Reserved (visitors, management)</option>
          <option value="MAINTENANCE">Closed for maintenance</option>
        </SelectField>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {slot ? 'Save changes' : 'Add slot'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
