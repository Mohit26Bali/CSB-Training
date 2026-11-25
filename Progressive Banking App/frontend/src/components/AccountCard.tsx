import { Link } from 'react-router-dom'
import type { Account } from '../api/types'
import { formatMoney, groupAccountNumber, titleCase } from '../lib/format'
import { Icon } from './Icon'
import { StatusBadge } from './ui'

/**
 * The bank-card style tile for one account: type, status, number and balance, with optional quick actions.
 * Used by pages/customer/DashboardPage, AccountsPage and AccountDetailPage. The gradient tells savings (blue) and
 * current (teal) apart at a glance; the decorative circles are pure CSS.
 */
const gradients = {
  SAVINGS: 'from-brand-900 via-brand-800 to-brand-600',
  CURRENT: 'from-slate-900 via-teal-900 to-teal-700',
} as const

export function AccountCard({
  account,
  onDeposit,
  onWithdraw,
  linkToDetails = true,
}: {
  account: Account
  onDeposit?: () => void
  onWithdraw?: () => void
  linkToDetails?: boolean
}) {
  const usable = account.status === 'ACTIVE'
  const body = (
    <>
      <span aria-hidden="true" className="pointer-events-none absolute -right-10 -top-12 size-44 rounded-full bg-white/8" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-16 right-10 size-52 rounded-full bg-white/6" />

      <div className="relative flex items-start justify-between">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.16em] text-white/65">{titleCase(account.accountType)} account</p>
          <p className="mt-3 text-[1.7rem] font-semibold leading-none tracking-tight tabular">{formatMoney(account.balance)}</p>
          <p className="mt-1 text-xs text-white/60">Available balance</p>
        </div>
        <span className="rounded-lg bg-white/15 p-1.5 text-white backdrop-blur">
          <Icon name="card" className="size-5" />
        </span>
      </div>

      <div className="relative mt-7 flex items-end justify-between gap-3">
        <div>
          <p className="font-mono text-sm tracking-[0.18em] text-white/90">{groupAccountNumber(account.accountNumber)}</p>
          <p className="mt-1 text-xs text-white/60">{account.customerName}</p>
        </div>
        <StatusPill status={account.status} />
      </div>
    </>
  )

  return (
    <div className="group">
      {linkToDetails ? (
        <Link
          to={`/accounts/${account.accountNumber}`}
          aria-label={`${titleCase(account.accountType)} account ${account.accountNumber}, balance ${formatMoney(account.balance)}`}
          className={`relative block overflow-hidden rounded-2xl bg-linear-to-br p-5 text-white shadow-lg shadow-brand-900/20 transition-transform duration-200 group-hover:-translate-y-0.5 ${gradients[account.accountType]}`}
        >
          {body}
        </Link>
      ) : (
        <div
          role="group"
          aria-label={`${titleCase(account.accountType)} account ${account.accountNumber}, balance ${formatMoney(account.balance)}`}
          className={`relative overflow-hidden rounded-2xl bg-linear-to-br p-5 text-white shadow-lg shadow-brand-900/20 ${gradients[account.accountType]}`}
        >
          {body}
        </div>
      )}
      {(onDeposit || onWithdraw) && (
        <div className="mt-3 grid grid-cols-2 gap-2">
          {onDeposit && (
            <button
              type="button"
              disabled={!usable}
              onClick={onDeposit}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-line bg-surface py-2 text-sm font-semibold text-ink transition-colors hover:bg-surface-2 disabled:cursor-not-allowed disabled:text-faint"
            >
              <Icon name="arrowDown" className="size-4 text-mint-600" /> Deposit
            </button>
          )}
          {onWithdraw && (
            <button
              type="button"
              disabled={!usable}
              onClick={onWithdraw}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-line bg-surface py-2 text-sm font-semibold text-ink transition-colors hover:bg-surface-2 disabled:cursor-not-allowed disabled:text-faint"
            >
              <Icon name="arrowUp" className="size-4 text-brand-600 dark:text-brand-300" /> Withdraw
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function StatusPill({ status }: { status: Account['status'] }) {
  const tone = { ACTIVE: 'bg-emerald-400/20 text-emerald-100', INACTIVE: 'bg-amber-400/25 text-amber-100', BLOCKED: 'bg-rose-400/25 text-rose-100' }[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold backdrop-blur ${tone}`}>
      <span className="size-1.5 rounded-full bg-current" />
      {titleCase(status)}
    </span>
  )
}

/** Re-export so pages that need a plain badge next to a card do not import ui.tsx twice. */
export { StatusBadge }
