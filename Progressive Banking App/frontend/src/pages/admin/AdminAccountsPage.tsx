import { useMemo, useState } from 'react'
import { errorMessage } from '../../api/client'
import { useAdminAccounts, useHistory, useUpdateAccountStatus } from '../../api/queries'
import type { Account, AccountStatus, AccountType } from '../../api/types'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { Pagination } from '../../components/Pagination'
import { useToast } from '../../components/Toast'
import { TransactionList } from '../../components/TransactionList'
import { Avatar, Button, EmptyState, ErrorState, Notice, PageHeader, Skeleton, StatusBadge } from '../../components/ui'
import { useDebounced } from '../../hooks'
import { formatDate, formatMoney, groupAccountNumber, titleCase } from '../../lib/format'

/**
 * Account administration (route /admin/accounts): search and filter every account, open its transaction history
 * (read-only), and activate / deactivate / block it. Status changes need a confirmation and go through
 * PATCH /api/admin/accounts/{no}/status (api/queries.ts#useUpdateAccountStatus). Administrators can read accounts
 * but the backend never lets them move money.
 */
const statusActions: { status: AccountStatus; label: string; variant: 'success' | 'secondary' | 'danger' }[] = [
  { status: 'ACTIVE', label: 'Activate', variant: 'success' },
  { status: 'INACTIVE', label: 'Deactivate', variant: 'secondary' },
  { status: 'BLOCKED', label: 'Block', variant: 'danger' },
]

export function AdminAccountsPage() {
  const accounts = useAdminAccounts()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | AccountStatus>('ALL')
  const [typeFilter, setTypeFilter] = useState<'ALL' | AccountType>('ALL')
  const [viewing, setViewing] = useState<Account | null>(null)
  const [changing, setChanging] = useState<{ account: Account; status: AccountStatus } | null>(null)
  const query = useDebounced(search.trim().toLowerCase())

  const filtered = useMemo(
    () =>
      (accounts.data ?? [])
        .filter((a) => statusFilter === 'ALL' || a.status === statusFilter)
        .filter((a) => typeFilter === 'ALL' || a.accountType === typeFilter)
        .filter((a) => !query || a.accountNumber.includes(query) || a.customerName.toLowerCase().includes(query)),
    [accounts.data, query, statusFilter, typeFilter],
  )

  const selectClass =
    'rounded-xl border border-line bg-surface px-3 py-2.5 text-sm text-ink focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15'

  return (
    <div>
      <PageHeader title="Accounts" subtitle="Search, review and manage customer accounts." />

      <div className="mb-5 flex flex-wrap gap-3">
        <div className="relative min-w-60 flex-1">
          <Icon name="search" className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-faint" />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or account number"
            aria-label="Search accounts"
            className="w-full rounded-xl border border-line bg-surface py-2.5 pl-10 pr-3.5 text-sm text-ink placeholder:text-faint focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15"
          />
        </div>
        <select aria-label="Filter by status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'ALL' | AccountStatus)} className={selectClass}>
          <option value="ALL">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
          <option value="BLOCKED">Blocked</option>
        </select>
        <select aria-label="Filter by type" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as 'ALL' | AccountType)} className={selectClass}>
          <option value="ALL">All types</option>
          <option value="SAVINGS">Savings</option>
          <option value="CURRENT">Current</option>
        </select>
      </div>

      {accounts.isLoading && (
        <div className="space-y-3">
          {[0, 1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-16 rounded-2xl" />
          ))}
        </div>
      )}
      {accounts.isError && <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />}
      {accounts.isSuccess && filtered.length === 0 && (
        <EmptyState icon="search" title="No accounts match" hint="Try a different search or clear the filters." />
      )}

      {accounts.isSuccess && filtered.length > 0 && (
        <div className="card-shadow overflow-hidden rounded-2xl border border-line bg-surface">
          <div className="hidden grid-cols-[2fr_1.4fr_1fr_1.2fr_0.9fr_auto] gap-4 border-b border-line bg-surface-2 px-5 py-3 text-xs font-semibold uppercase tracking-wide text-faint lg:grid">
            <span>Customer</span>
            <span>Account</span>
            <span>Type</span>
            <span className="text-right">Balance</span>
            <span>Status</span>
            <span className="w-52 text-right">Actions</span>
          </div>
          <ul className="divide-y divide-line">
            {filtered.map((a) => (
              <li key={a.accountNumber} className="grid items-center gap-x-4 gap-y-3 px-5 py-4 lg:grid-cols-[2fr_1.4fr_1fr_1.2fr_0.9fr_auto]">
                <div className="flex min-w-0 items-center gap-3">
                  <Avatar name={a.customerName} size="sm" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-ink">{a.customerName}</p>
                    <p className="text-xs text-muted">Opened {formatDate(a.createdAt)}</p>
                  </div>
                </div>
                <p className="font-mono text-sm tracking-wider text-ink">{groupAccountNumber(a.accountNumber)}</p>
                <p className="text-sm text-muted">{titleCase(a.accountType)}</p>
                <p className="text-sm font-semibold text-ink tabular lg:text-right">{formatMoney(a.balance)}</p>
                <div>
                  <StatusBadge status={a.status} />
                </div>
                <div className="flex flex-wrap gap-2 lg:w-52 lg:justify-end">
                  <Button variant="secondary" small onClick={() => setViewing(a)}>
                    History
                  </Button>
                  <select
                    aria-label={`Change status of account ${a.accountNumber}`}
                    value=""
                    onChange={(e) => e.target.value && setChanging({ account: a, status: e.target.value as AccountStatus })}
                    className="rounded-xl border border-line bg-surface px-2.5 py-1.5 text-sm text-ink focus:border-brand-500 focus:outline-none"
                  >
                    <option value="">Status…</option>
                    {statusActions
                      .filter((action) => action.status !== a.status)
                      .map((action) => (
                        <option key={action.status} value={action.status}>
                          {action.label}
                        </option>
                      ))}
                  </select>
                </div>
              </li>
            ))}
          </ul>
          <p className="border-t border-line bg-surface-2 px-5 py-3 text-xs text-muted">
            Showing {filtered.length} of {accounts.data.length} accounts
          </p>
        </div>
      )}

      <HistoryModal account={viewing} onClose={() => setViewing(null)} />
      <StatusModal change={changing} onClose={() => setChanging(null)} />
    </div>
  )
}

function HistoryModal({ account, onClose }: { account: Account | null; onClose: () => void }) {
  return (
    <Modal
      open={account !== null}
      wide
      title="Transaction history"
      description={account ? `${account.customerName} · ${titleCase(account.accountType)} · ${groupAccountNumber(account.accountNumber)}` : undefined}
      onClose={onClose}
    >
      {account && <HistoryBody key={account.accountNumber} accountNumber={account.accountNumber} />}
    </Modal>
  )
}

function HistoryBody({ accountNumber }: { accountNumber: string }) {
  const [page, setPage] = useState(0)
  const history = useHistory(accountNumber, page, 8)
  if (history.isLoading) return <Skeleton className="h-56" />
  if (history.isError) return <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />
  if (!history.data || history.data.content.length === 0) return <p className="py-10 text-center text-sm text-muted">No transactions yet.</p>
  return (
    <>
      <TransactionList transactions={history.data.content} />
      <Pagination page={history.data.page} totalPages={history.data.totalPages} totalElements={history.data.totalElements} onPage={setPage} />
    </>
  )
}

function StatusModal({ change, onClose }: { change: { account: Account; status: AccountStatus } | null; onClose: () => void }) {
  const toast = useToast()
  const update = useUpdateAccountStatus()
  const [error, setError] = useState<string | null>(null)
  const action = statusActions.find((a) => a.status === change?.status)

  async function apply() {
    if (!change) return
    setError(null)
    try {
      await update.mutateAsync({ accountNumber: change.account.accountNumber, status: change.status })
      toast.success(`Account ${groupAccountNumber(change.account.accountNumber)} is now ${change.status.toLowerCase()}`)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal open={change !== null} title={`${action?.label ?? 'Change'} account?`} onClose={() => { setError(null); onClose() }}>
      {change && (
        <div className="space-y-4">
          {error && <Notice tone="error">{error}</Notice>}
          <p className="text-sm text-muted">
            {change.account.customerName}’s {titleCase(change.account.accountType).toLowerCase()} account{' '}
            <span className="font-mono tracking-wider text-ink">{groupAccountNumber(change.account.accountNumber)}</span> will become{' '}
            <strong className="text-ink">{change.status.toLowerCase()}</strong>.
          </p>
          {change.status !== 'ACTIVE' && (
            <Notice tone="warning">The customer will not be able to deposit, withdraw, transfer or see the balance until it is activated again.</Notice>
          )}
          <div className="flex gap-3 pt-1">
            <Button variant="secondary" className="flex-1" onClick={() => { setError(null); onClose() }}>
              Cancel
            </Button>
            <Button variant={action?.variant === 'secondary' ? 'primary' : action?.variant} className="flex-1" loading={update.isPending} onClick={apply}>
              {action?.label}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}
