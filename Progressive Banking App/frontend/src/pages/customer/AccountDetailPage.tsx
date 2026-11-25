import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/client'
import { useAccount, useHistory } from '../../api/queries'
import type { Account, TransactionFilters } from '../../api/types'
import { AccountCard } from '../../components/AccountCard'
import { Icon } from '../../components/Icon'
import { Pagination } from '../../components/Pagination'
import { TransactionFilterBar } from '../../components/TransactionFilterBar'
import { useToast } from '../../components/Toast'
import { TransactionList } from '../../components/TransactionList'
import { Button, Card, EmptyState, ErrorState, LoadingState, Notice, Skeleton, StatusBadge } from '../../components/ui'
import { activeFilterCount } from '../../lib/filters'
import { formatDate, groupAccountNumber, titleCase } from '../../lib/format'
import { MoneyModal } from './MoneyModal'

/**
 * One account (route /accounts/:accountNumber): the card, its details, deposit / withdraw / transfer actions and the
 * paged transaction history (newest first). A number that is not the caller's gets a 404 from the backend, which is
 * shown as "not found" here.
 */
export function AccountDetailPage() {
  const { accountNumber } = useParams()
  const account = useAccount(accountNumber)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TransactionFilters>({})
  const history = useHistory(accountNumber, page, 10, filters)
  const [mode, setMode] = useState<'deposit' | 'withdraw' | null>(null)

  if (account.isLoading) return <LoadingState label="Loading account…" />
  if (account.isError) {
    const notFound = account.error instanceof ApiError && account.error.status === 404
    return (
      <div className="space-y-4">
        <BackLink />
        {notFound ? (
          <EmptyState icon="search" title="Account not found" hint="It may not exist, or it doesn’t belong to you." />
        ) : (
          <ErrorState message={errorMessage(account.error)} onRetry={() => account.refetch()} />
        )}
      </div>
    )
  }

  const a = account.data!
  const usable = a.status === 'ACTIVE'

  return (
    <div className="space-y-6">
      <BackLink />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,26rem)_1fr]">
        <div className="space-y-4">
          <AccountCard account={a} linkToDetails={false} />
          {!usable && (
            <Notice tone="warning">
              This account is {a.status.toLowerCase()}, so deposits, withdrawals and transfers are paused. Please contact the bank.
            </Notice>
          )}
          <div className="grid grid-cols-3 gap-2">
            <Button variant="secondary" disabled={!usable} onClick={() => setMode('deposit')}>
              <Icon name="arrowDown" className="size-4 text-mint-600" /> Deposit
            </Button>
            <Button variant="secondary" disabled={!usable} onClick={() => setMode('withdraw')}>
              <Icon name="arrowUp" className="size-4 text-brand-600 dark:text-brand-300" /> Withdraw
            </Button>
            {usable ? (
              <Link
                to={`/transfer?from=${a.accountNumber}`}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
              >
                <Icon name="send" className="size-4" /> Transfer
              </Link>
            ) : (
              <Button disabled>
                <Icon name="send" className="size-4" /> Transfer
              </Button>
            )}
          </div>
        </div>

        <Details account={a} />
      </div>

      <Card>
        <h2 className="mb-3 text-lg font-semibold text-ink">Transaction history</h2>
        <TransactionFilterBar
          value={filters}
          onChange={(next) => {
            setFilters(next)
            setPage(0)
          }}
        />
        {history.isLoading && (
          <div className="space-y-3 py-2">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        )}
        {history.isError && <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />}
        {history.isSuccess && history.data.content.length === 0 && (
          <p className="py-10 text-center text-sm text-muted">
            {activeFilterCount(filters) > 0 ? 'No transactions match these filters.' : 'No transactions on this account yet.'}
          </p>
        )}
        {history.isSuccess && history.data.content.length > 0 && (
          <>
            <TransactionList transactions={history.data.content} />
            <Pagination page={history.data.page} totalPages={history.data.totalPages} totalElements={history.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>

      <MoneyModal mode={mode ?? 'deposit'} account={mode ? a : null} onClose={() => setMode(null)} />
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/accounts" className="inline-flex items-center gap-1.5 text-sm font-medium text-muted transition-colors hover:text-ink">
      <Icon name="arrowLeft" className="size-4" /> All accounts
    </Link>
  )
}

function Details({ account }: { account: Account }) {
  const toast = useToast()
  const rows: [string, React.ReactNode][] = [
    ['Account holder', account.customerName],
    ['Account type', `${titleCase(account.accountType)} account`],
    ['Status', <StatusBadge key="s" status={account.status} />],
    ['Opened on', formatDate(account.createdAt)],
  ]

  async function copy() {
    try {
      await navigator.clipboard.writeText(account.accountNumber)
      toast.success('Account number copied')
    } catch {
      toast.error('Could not copy. Select the number and copy it manually.')
    }
  }

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink">Account details</h2>
      <dl className="mt-4 divide-y divide-line">
        <div className="flex items-center justify-between gap-4 py-3">
          <dt className="text-sm text-muted">Account number</dt>
          <dd className="flex items-center gap-2">
            <span className="font-mono text-sm font-semibold tracking-wider text-ink">{groupAccountNumber(account.accountNumber)}</span>
            <button
              type="button"
              onClick={copy}
              aria-label="Copy account number"
              className="grid size-8 place-items-center rounded-lg text-faint transition-colors hover:bg-brand-500/10 hover:text-ink"
            >
              <Icon name="copy" className="size-4" />
            </button>
          </dd>
        </div>
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between gap-4 py-3">
            <dt className="text-sm text-muted">{label}</dt>
            <dd className="text-sm font-medium text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  )
}
