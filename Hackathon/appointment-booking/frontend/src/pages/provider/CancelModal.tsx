import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import { useToast } from '../../components/Toast'
import { formatTime, longDay } from '../../dates'
import { Button, Modal, TextAreaField } from '../../components/ui'

export interface CancelTarget {
  id: number
  number: string
  customerName: string
  start: string
}

/** The provider cancels an appointment and must say why; the customer sees the reason. */
export function CancelModal({ appointment, onClose }: { appointment: CancelTarget; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const cancel = useMutation({
    mutationFn: () => providerApi.cancel(appointment.id, reason),
    onSuccess: (a) => {
      toast.success(`${a.number} is cancelled and the slot is open again`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      setError(err instanceof ApiError && err.fieldErrors.reason ? err.fieldErrors.reason : errorMessage(err))
      queryClient.invalidateQueries()
    },
  })

  return (
    <Modal title={`Cancel ${appointment.number}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          {appointment.customerName}, {longDay(appointment.start)} at {formatTime(appointment.start)}. They will see your reason.
        </p>
        <TextAreaField label="Reason" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} error={error ?? undefined} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep it
          </Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel appointment
          </Button>
        </div>
      </div>
    </Modal>
  )
}
