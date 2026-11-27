import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { listCustomers, setCustomerEnabled } from '../api/admin'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorState, PageSpinner } from '../components/ui'
import { errorMessage } from '../hooks'
import { formatDate, formatPrice } from '../lib/format'
import { PageHeader, Pager, td, th, Toggle } from './AdminLayout'

export function CustomersAdminPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const customers = useQuery({ queryKey: ['admin', 'customers', q, page], queryFn: () => listCustomers({ q, page }), placeholderData: keepPreviousData })
  const toggle = useMutation({
    mutationFn: ({ id, on }: { id: number; on: boolean }) => setCustomerEnabled(id, on),
    onSuccess: (_, { on }) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] })
      toast(on ? 'Account enabled' : 'Account disabled: the customer is logged out')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <div>
      <PageHeader title="Customers">
        <form className="mt-4 flex gap-2 sm:max-w-md" onSubmit={(e: FormEvent) => { e.preventDefault(); setQ(search.trim()); setPage(0) }}>
          <label htmlFor="customer-search" className="sr-only">Search customers</label>
          <input id="customer-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name or email" className="flex-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" />
          <Button type="submit" variant="outline">Search</Button>
        </form>
      </PageHeader>
      {customers.isPending ? <PageSpinner /> : customers.isError ? <ErrorState message={errorMessage(customers.error)} /> : (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead className="bg-slate-50">
                <tr><th className={th}>Customer</th><th className={th}>Phone</th><th className={th}>Joined</th><th className={`${th} text-right`}>Orders</th><th className={`${th} text-right`}>Spent</th><th className={th}>Enabled</th></tr>
              </thead>
              <tbody>
                {customers.data.items.length === 0 && <tr><td colSpan={6} className="px-4 py-10 text-center text-slate-500">No customers match.</td></tr>}
                {customers.data.items.map((c) => (
                  <tr key={c.id} className="border-t border-slate-100">
                    <td className={td}><span className="font-medium">{c.fullName}</span><span className="block text-xs text-slate-500">{c.email}</span></td>
                    <td className={td}>{c.phone ?? '–'}</td>
                    <td className={td}>{formatDate(c.joinedAt)}</td>
                    <td className={`${td} text-right`}>{c.orderCount}</td>
                    <td className={`${td} text-right`}>{formatPrice(c.totalSpent)}</td>
                    <td className={td}><Toggle checked={c.enabled} label={`${c.fullName} enabled`} onChange={(on) => toggle.mutate({ id: c.id, on })} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={customers.data.page} totalPages={customers.data.totalPages} totalItems={customers.data.totalItems} onPage={setPage} />
        </Card>
      )}
    </div>
  )
}
