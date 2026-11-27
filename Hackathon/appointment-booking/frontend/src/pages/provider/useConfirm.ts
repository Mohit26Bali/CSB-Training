import { useMutation, useQueryClient } from '@tanstack/react-query'
import { errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import { useToast } from '../../components/Toast'

/** Confirms a pending appointment and refreshes everything that shows it. */
export function useConfirm() {
  const toast = useToast()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => providerApi.confirm(id),
    onSuccess: (a) => {
      toast.success(`${a.number} confirmed for ${a.customerName}`)
      queryClient.invalidateQueries()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      queryClient.invalidateQueries()
    },
  })
}
