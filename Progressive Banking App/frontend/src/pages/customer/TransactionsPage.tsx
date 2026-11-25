import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { useHistory, useMyAccounts } from '../../api/queries'
import type { Transaction, TransactionFilters } from '../../api/types'
import { Icon } from '../../components/Icon'
import { Pagination } from '../../components/Pagination'
import { TransactionFilterBar } from '../../components/TransactionFilterBar'
import { TransactionList } from '../../components/TransactionList'
import { Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, SelectField } from '../../components/ui'
import { activeFilterCount } from '../../lib/filters'
import { formatMoney, groupAccountNumber, titleCase } from '../../lib/format'

/**
 * Statement view (route /transactions): pick one of your accounts and page through its history, newest first.
 * Filters (components/TransactionFilterBar) narrow the list on the server; "Export CSV" downloads the page currently
 * on screen, filters included (built in the browser, nothing is sent anywhere).
 * Data: api/queries.ts#useHistory -> GET /api/accounts/{no}/transactions.
 */
function toCsv(rows: Transaction[]): string {
  const header = ['Date', 'Type', 'Direction', 'Amount', 'Status', 'Counterparty', 'Description', 'Balance after', 'Reference']
  const escape = (value: string | number | null) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const lines = rows.map((t) =>
    [t.createdAt, t.type, t.direction, t.amount, t.status, t.counterpartyAccountNumber, t.description, t.balanceAfter, t.transactionRef].map(escape).join(','),
  )
  return [header.join(','), ...lines].join('\r\n')
}

export function TransactionsPage() {
  const accounts = useMyAccounts()
  const [selected, setSelected] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TransactionFilters>({})

  const accountNumber = selected ?? accounts.data?.[0]?.accountNumber
  const history = useHistory(accountNumber, page, 15, filters)

  function download() {
    if (!history.data || !accountNumber) return
    const blob = new Blob([toCsv(history.data.content)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `statement-${accountNumber}-page-${page + 1}${activeFilterCount(filters) > 0 ? '-filtered' : ''}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div>
      <PageHeader
        title="Transactions"
        subtitle="Every deposit, withdrawal and transfer, newest first."
        actions={
          <Button variant="secondary" disabled={!history.data?.content.length} onClick={download}>
            <Icon name="receipt" className="size-4" /> Export CSV
          </Button>
        }
      />

      {accounts.isError && <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />}
      {accounts.isSuccess && accounts.data.length === 0 && (
        <EmptyState
          icon="wallet"
          title="No accounts yet"
          hint="Open an account to see its transactions here."
          action={<Link to="/accounts" className="font-semibold text-brand-600 hover:underline dark:text-brand-300">Go to my accounts</Link>}
        />
      )}

      {accounts.isSuccess && accounts.data.length > 0 && (
        <Card>
          <div className="mb-4 max-w-sm">
            <SelectField
              label="Account"
              value={accountNumber ?? ''}
              onChange={(e) => {
                setSelected(e.target.value)
                setPage(0)
              }}
            >
              {accounts.data.map((a) => (
                <option key={a.accountNumber} value={a.accountNumber}>
                  {titleCase(a.accountType)} · {groupAccountNumber(a.accountNumber)} · {formatMoney(a.balance)}
                </option>
              ))}
            </SelectField>
          </div>

          <TransactionFilterBar
            value={filters}
            onChange={(next) => {
              setFilters(next)
              setPage(0)
            }}
          />

          {history.isLoading && (
            <div className="space-y-3 py-2">
              {[0, 1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </div>
          )}
          {history.isError && <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />}
          {history.isSuccess && history.data.content.length === 0 && (
            <p className="py-12 text-center text-sm text-muted">
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
      )}
    </div>
  )
}
