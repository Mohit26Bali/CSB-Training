import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { loanApi } from '../../api/endpoints'
import type { Loan } from '../../api/types'
import { Banner, BookCover } from '../../components/BookCover'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, EmptyState, ErrorState, formatDate, formatDay, formatMoney, LoadingState, StatusBadge } from '../../components/ui'

function LoanRow({ loan, onCancel }: { loan: Loan; onCancel?: () => void }) {
  return (
    <li className={`flex overflow-hidden rounded-xl border bg-white shadow-sm ${loan.overdue ? 'border-rose-300' : 'border-slate-200'}`}>
      <BookCover title={loan.bookTitle} author={loan.bookAuthor} category={loan.category} className="hidden w-20 shrink-0 sm:flex" />
      <div className="flex flex-1 flex-wrap items-center justify-between gap-3 p-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold text-slate-900">{loan.bookTitle}</h3>
            <StatusBadge status={loan.overdue ? 'OVERDUE' : loan.status} />
          </div>
          <p className="text-sm text-slate-600">{loan.bookAuthor}</p>
          {loan.status === 'RESERVED' && <p className="mt-1 text-xs text-slate-500">Reserved {formatDate(loan.reservedAt)}. Collect it from the desk.</p>}
          {loan.status === 'ISSUED' && loan.dueDate && (
            <p className={`mt-1 text-sm ${loan.overdue ? 'font-medium text-rose-700' : 'text-slate-600'}`}>
              {loan.overdue
                ? `${loan.daysOverdue} day${loan.daysOverdue === 1 ? '' : 's'} overdue (due ${formatDay(loan.dueDate)}). Fine so far ${formatMoney(loan.fine)}`
                : `Due ${formatDay(loan.dueDate)}`}
            </p>
          )}
          {loan.status === 'RETURNED' && (
            <p className="mt-1 text-xs text-slate-500">
              Returned {formatDate(loan.returnedAt)}
              {loan.fine > 0 && `, fine paid ${formatMoney(loan.fine)}`}
            </p>
          )}
        </div>
        {onCancel && (
          <Button variant="secondary" small onClick={onCancel}>
            Cancel reservation
          </Button>
        )}
      </div>
    </li>
  )
}

export function ShelfPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [cancelling, setCancelling] = useState<Loan | null>(null)
  const loans = useQuery({ queryKey: ['loans', 'mine'], queryFn: () => loanApi.mine() })

  const cancel = useMutation({
    mutationFn: (l: Loan) => loanApi.cancel(l.id),
    onSuccess: (l) => toast.success(`Reservation for “${l.bookTitle}” cancelled`),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setCancelling(null)
      queryClient.invalidateQueries()
    },
  })

  if (loans.isPending) return <LoadingState />
  if (loans.isError) return <ErrorState message={errorMessage(loans.error)} onRetry={() => loans.refetch()} />

  const out = loans.data.filter((l) => l.status === 'ISSUED')
  const reserved = loans.data.filter((l) => l.status === 'RESERVED')
  const history = loans.data.filter((l) => l.status === 'RETURNED' || l.status === 'CANCELLED')
  const owed = out.reduce((sum, l) => sum + l.fine, 0)

  return (
    <>
      <Banner src="/banner/reading.jpg" title="My shelf" text="What you have, what is waiting for you and what you have read." />

      {owed > 0 && (
        <p role="alert" className="mb-6 rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-800">
          You have overdue books. Return them to stop the fine growing (now {formatMoney(owed)}). You cannot reserve more books until then.
        </p>
      )}

      <h2 className="mb-3 text-lg font-semibold text-slate-900">On loan ({out.length})</h2>
      {out.length === 0 ? (
        <EmptyState
          title="Nothing on loan"
          action={
            <Link to="/catalogue" className="font-medium text-brand-700 hover:underline">
              Browse the catalogue
            </Link>
          }
        />
      ) : (
        <ul className="space-y-3">
          {out.map((l) => (
            <LoanRow key={l.id} loan={l} />
          ))}
        </ul>
      )}

      <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Reserved ({reserved.length})</h2>
      {reserved.length === 0 ? (
        <p className="text-sm text-slate-500">No reservations waiting at the desk.</p>
      ) : (
        <ul className="space-y-3">
          {reserved.map((l) => (
            <LoanRow key={l.id} loan={l} onCancel={() => setCancelling(l)} />
          ))}
        </ul>
      )}

      <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">History</h2>
      {history.length === 0 ? (
        <p className="text-sm text-slate-500">Nothing yet.</p>
      ) : (
        <ul className="space-y-3">
          {history.map((l) => (
            <LoanRow key={l.id} loan={l} />
          ))}
        </ul>
      )}

      {cancelling && (
        <ConfirmDialog
          title="Cancel this reservation?"
          message={`“${cancelling.bookTitle}” goes back on the shelf for other readers.`}
          confirmLabel="Cancel reservation"
          danger
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(cancelling)}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}
