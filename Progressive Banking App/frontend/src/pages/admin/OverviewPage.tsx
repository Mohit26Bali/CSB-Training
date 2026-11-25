import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { useAdminAccounts } from '../../api/queries'
import type { Account } from '../../api/types'
import { Icon, type IconName } from '../../components/Icon'
import { Avatar, Card, ErrorState, PageHeader, Skeleton, StatusBadge } from '../../components/ui'
import { formatDate, formatMoney, formatMoneyShort, groupAccountNumber, sumMoney, titleCase } from '../../lib/format'

/**
 * Administrator home (route /admin): headline numbers and breakdowns computed in the browser from the full account
 * list (GET /api/admin/accounts via api/queries.ts#useAdminAccounts). With a large customer base these figures would
 * come from dedicated aggregate endpoints instead; for this project one list call is simpler and always consistent.
 */
function Stat({ icon, label, value, tone }: { icon: IconName; label: string; value: string; tone: string }) {
  return (
    <Card className="flex items-center gap-4">
      <span className={`grid size-12 shrink-0 place-items-center rounded-2xl ${tone}`}>
        <Icon name={icon} className="size-6" />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-muted">{label}</p>
        <p className="truncate text-2xl font-semibold tracking-tight text-ink tabular">{value}</p>
      </div>
    </Card>
  )
}

function Breakdown({ title, rows }: { title: string; rows: { label: string; value: number; color: string }[] }) {
  const total = rows.reduce((sum, r) => sum + r.value, 0)
  return (
    <Card>
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
      <div className="mt-4 flex h-3 overflow-hidden rounded-full bg-line" role="img" aria-label={rows.map((r) => `${r.label}: ${r.value}`).join(', ')}>
        {rows.map((r) => (
          <span key={r.label} className={r.color} style={{ width: total ? `${(r.value / total) * 100}%` : 0 }} />
        ))}
      </div>
      <ul className="mt-4 space-y-2">
        {rows.map((r) => (
          <li key={r.label} className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2 text-muted">
              <span className={`size-2.5 rounded-full ${r.color}`} /> {r.label}
            </span>
            <span className="font-semibold text-ink tabular">
              {r.value} <span className="font-normal text-faint">({total ? Math.round((r.value / total) * 100) : 0}%)</span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

export function OverviewPage() {
  const accounts = useAdminAccounts()

  if (accounts.isError) {
    return (
      <div>
        <PageHeader title="Overview" />
        <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />
      </div>
    )
  }

  const list: Account[] = accounts.data ?? []
  const count = (pred: (a: Account) => boolean) => list.filter(pred).length
  const customers = new Set(list.map((a) => a.customerName)).size

  return (
    <div className="space-y-6">
      <PageHeader title="Overview" subtitle="A live summary of every account at the bank." />

      <section className="fade-up relative overflow-hidden rounded-3xl bg-brand-950 text-white">
        <img src="/images/team.jpg" alt="" className="absolute inset-0 size-full object-cover opacity-30" />
        <div className="absolute inset-0 bg-linear-to-r from-brand-950 via-brand-950/85 to-transparent" />
        <div className="relative p-6 sm:p-8">
          <p className="text-xs font-medium uppercase tracking-[0.16em] text-white/60">Total deposits held</p>
          {accounts.isLoading ? (
            <Skeleton className="mt-2 h-11 w-72 !bg-white/15" />
          ) : (
            <p className="mt-1 text-4xl font-semibold tracking-tight tabular sm:text-5xl">{formatMoney(sumMoney(list.map((a) => a.balance)))}</p>
          )}
          <p className="mt-2 text-sm text-white/65">{accounts.isLoading ? ' ' : `${list.length} accounts · ${customers} customers`}</p>
        </div>
      </section>

      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {accounts.isLoading
          ? [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24 rounded-2xl" />)
          : [
              <Stat key="a" icon="wallet" label="Total accounts" value={String(list.length)} tone="bg-brand-500/12 text-brand-600 dark:text-brand-300" />,
              <Stat key="b" icon="checkCircle" label="Active" value={String(count((a) => a.status === 'ACTIVE'))} tone="bg-emerald-500/12 text-emerald-600 dark:text-emerald-300" />,
              <Stat key="c" icon="alert" label="Inactive" value={String(count((a) => a.status === 'INACTIVE'))} tone="bg-amber-500/14 text-amber-600 dark:text-amber-300" />,
              <Stat key="d" icon="shield" label="Blocked" value={String(count((a) => a.status === 'BLOCKED'))} tone="bg-rose-500/12 text-rose-600 dark:text-rose-300" />,
            ]}
      </div>

      {!accounts.isLoading && (
        <div className="grid gap-5 lg:grid-cols-2">
          <Breakdown
            title="Accounts by status"
            rows={[
              { label: 'Active', value: count((a) => a.status === 'ACTIVE'), color: 'bg-mint-500' },
              { label: 'Inactive', value: count((a) => a.status === 'INACTIVE'), color: 'bg-amber-500' },
              { label: 'Blocked', value: count((a) => a.status === 'BLOCKED'), color: 'bg-rose-500' },
            ]}
          />
          <Breakdown
            title="Accounts by type"
            rows={[
              { label: 'Savings', value: count((a) => a.accountType === 'SAVINGS'), color: 'bg-brand-500' },
              { label: 'Current', value: count((a) => a.accountType === 'CURRENT'), color: 'bg-teal-500' },
            ]}
          />
        </div>
      )}

      <Card>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-ink">Recently opened</h2>
          <Link to="/admin/accounts" className="text-sm font-semibold text-brand-600 hover:underline dark:text-brand-300">
            Manage accounts
          </Link>
        </div>
        {accounts.isLoading ? (
          <div className="space-y-3 py-2">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : (
          <ul className="divide-y divide-line">
            {list.slice(0, 5).map((a) => (
              <li key={a.accountNumber} className="flex items-center gap-4 py-3">
                <Avatar name={a.customerName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-ink">{a.customerName}</p>
                  <p className="text-xs text-muted">
                    {titleCase(a.accountType)} · {groupAccountNumber(a.accountNumber)} · opened {formatDate(a.createdAt)}
                  </p>
                </div>
                <StatusBadge status={a.status} />
                <p className="hidden w-32 text-right text-sm font-semibold text-ink tabular sm:block">{formatMoneyShort(a.balance)}</p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
