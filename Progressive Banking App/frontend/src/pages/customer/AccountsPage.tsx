import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { useMyAccounts } from '../../api/queries'
import type { Account } from '../../api/types'
import { AccountCard } from '../../components/AccountCard'
import { Icon } from '../../components/Icon'
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'
import { MoneyModal } from './MoneyModal'
import { OpenAccountModal } from './OpenAccountModal'

/** All of the customer's accounts (route /accounts) with quick deposit / withdraw and "open a new account". */
export function AccountsPage() {
  const accounts = useMyAccounts()
  const [money, setMoney] = useState<{ mode: 'deposit' | 'withdraw'; account: Account } | null>(null)
  const [opening, setOpening] = useState(false)

  return (
    <div>
      <PageHeader
        title="My accounts"
        subtitle="Your savings and current accounts in one place."
        actions={
          <Button onClick={() => setOpening(true)}>
            <Icon name="plus" className="size-4" /> New account
          </Button>
        }
      />

      {accounts.isLoading && (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}
      {accounts.isError && <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />}
      {accounts.isSuccess && accounts.data.length === 0 && (
        <EmptyState
          icon="wallet"
          title="No accounts yet"
          hint="Open a savings or current account to get started."
          action={<Button onClick={() => setOpening(true)}>Open an account</Button>}
        />
      )}
      {accounts.isSuccess && accounts.data.length > 0 && (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {accounts.data.map((account, i) => (
            <div key={account.accountNumber} className="fade-up" style={{ animationDelay: `${i * 0.06}s` }}>
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

      <MoneyModal mode={money?.mode ?? 'deposit'} account={money?.account ?? null} onClose={() => setMoney(null)} />
      <OpenAccountModal open={opening} onClose={() => setOpening(false)} />
    </div>
  )
}
