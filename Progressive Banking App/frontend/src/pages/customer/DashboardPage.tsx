import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { useMyAccounts, useRecentActivity } from '../../api/queries'
import type { Account, Transaction } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { AccountCard } from '../../components/AccountCard'
import { Icon } from '../../components/Icon'
import { TransactionList } from '../../components/TransactionList'
import { Button, Card, EmptyState, ErrorState, Skeleton } from '../../components/ui'
import { formatMoney, greeting, sumMoney } from '../../lib/format'
import { MoneyModal } from './MoneyModal'
import { OpenAccountModal } from './OpenAccountModal'

/**
 * Customer home (route /dashboard): a welcome banner with the combined balance, the customer's account cards with
 * quick deposit / withdraw, and a merged "recent activity" feed. Data comes from api/queries.ts (accounts, plus the
 * newest 5 history lines of each account, merged and sorted here). The banner photo is public/images/banner-dusk.jpg.
 */
export function DashboardPage() {
  const { user } = useAuth()
  const accounts = useMyAccounts()
  const [money, setMoney] = useState<{ mode: 'deposit' | 'withdraw'; account: Account } | null>(null)
  const [opening, setOpening] = useState(false)

  const list = accounts.data ?? []
  const total = sumMoney(list.filter((a) => a.status === 'ACTIVE').map((a) => a.balance))
  const firstName = user?.fullName.split(' ')[0] ?? ''

  return (
    <div className="space-y-8">
      <section className="fade-up relative overflow-hidden rounded-3xl bg-brand-950 text-white shadow-xl shadow-brand-900/20">
        <img src="/images/banner-dusk.jpg" alt="" className="absolute inset-0 size-full object-cover opacity-90" />
        <div className="absolute inset-0 bg-linear-to-r from-brand-950 via-brand-950/70 to-brand-950/10" />
        <div className="relative flex flex-col gap-8 p-6 sm:p-9 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-sm font-medium text-white/70">
              {greeting()}, {firstName}
            </p>
            <p className="mt-5 text-xs font-medium uppercase tracking-[0.16em] text-white/60">Total balance</p>
            {accounts.isLoading ? (
              <Skeleton className="mt-2 h-11 w-64 !bg-white/15" />
            ) : (
              <p className="mt-1 text-4xl font-semibold tracking-tight tabular sm:text-5xl">{formatMoney(total)}</p>
            )}
            <p className="mt-2 text-sm text-white/60">
              {accounts.isLoading ? ' ' : `Across ${list.length} account${list.length === 1 ? '' : 's'}`}
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link
              to="/transfer"
              className="inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-brand-900 shadow-sm transition-transform hover:-translate-y-0.5"
            >
              <Icon name="send" className="size-4" /> Transfer money
            </Link>
            <button
              type="button"
              onClick={() => setOpening(true)}
              className="inline-flex items-center gap-2 rounded-xl border border-white/30 bg-white/10 px-4 py-2.5 text-sm font-semibold text-white backdrop-blur transition-colors hover:bg-white/20"
            >
              <Icon name="plus" className="size-4" /> New account
            </button>
          </div>
        </div>
      </section>

      <section aria-labelledby="accounts-heading">
        <div className="mb-4 flex items-center justify-between">
          <h2 id="accounts-heading" className="text-lg font-semibold text-ink">
            Your accounts
          </h2>
          {list.length > 0 && (
            <Link to="/accounts" className="text-sm font-semibold text-brand-600 hover:underline dark:text-brand-300">
              View all
            </Link>
          )}
        </div>

        {accounts.isLoading && (
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {[0, 1].map((i) => (
              <Skeleton key={i} className="h-44 rounded-2xl" />
            ))}
          </div>
        )}
        {accounts.isError && <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />}
        {accounts.isSuccess && list.length === 0 && (
          <EmptyState
            icon="wallet"
            title="You don’t have an account yet"
            hint="Open a savings or current account to start depositing, withdrawing and transferring money."
            action={<Button onClick={() => setOpening(true)}>Open your first account</Button>}
          />
        )}
        {accounts.isSuccess && list.length > 0 && (
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {list.slice(0, 3).map((account, i) => (
              <div key={account.accountNumber} className="fade-up" style={{ animationDelay: `${i * 0.07}s` }}>
                <AccountCard
                  account={account}
                  onDeposit={() => setMoney({ mode: 'deposit', account })}
                  onWithdraw={() => setMoney({ mode: 'withdraw', account })}
                />
              </div>
            ))}
            <button
              type="button"
              onClick={() => setOpening(true)}
              className="flex min-h-44 flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-line text-muted transition-colors hover:border-brand-400 hover:bg-brand-500/5 hover:text-brand-600 dark:hover:text-brand-300"
            >
              <span className="grid size-11 place-items-center rounded-full bg-brand-500/10">
                <Icon name="plus" className="size-5" />
              </span>
              <span className="text-sm font-semibold">Open a new account</span>
            </button>
          </div>
        )}
      </section>

      {accounts.isSuccess && list.length > 0 && <RecentActivity accounts={list} />}

      <MoneyModal mode={money?.mode ?? 'deposit'} account={money?.account ?? null} onClose={() => setMoney(null)} />
      <OpenAccountModal open={opening} onClose={() => setOpening(false)} />
    </div>
  )
}

function RecentActivity({ accounts }: { accounts: Account[] }) {
  const results = useRecentActivity(accounts.map((a) => a.accountNumber))
  const loading = results.some((r) => r.isLoading)

  // Merge the newest lines of every account, newest first. A transfer between two of the customer's own accounts
  // appears once per account (a debit and a credit), which is accurate: both balances changed.
  const items: Transaction[] = results
    .flatMap((r) => r.data?.content ?? [])
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 6)

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-ink">Recent activity</h2>
        <Link to="/transactions" className="text-sm font-semibold text-brand-600 hover:underline dark:text-brand-300">
          See all
        </Link>
      </div>
      {loading ? (
        <div className="space-y-3 py-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted">No transactions yet. Make a deposit to get started.</p>
      ) : (
        <TransactionList transactions={items} compact />
      )}
    </Card>
  )
}
