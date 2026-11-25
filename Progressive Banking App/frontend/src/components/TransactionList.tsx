import type { Transaction } from '../api/types'
import { formatDate, formatMoney, formatTime, groupAccountNumber } from '../lib/format'
import { Icon } from './Icon'
import { TxStatusBadge } from './ui'

/**
 * Renders ledger lines (api Transaction) as a responsive list: icon, title, date, status, signed amount and the
 * balance after the entry. Used by pages/customer/AccountDetailPage, TransactionsPage, DashboardPage (recent
 * activity) and the admin account viewer. Pure presentation: the caller fetches the data (api/queries.ts).
 *
 * Reading the sign: CREDIT adds money (green, +), DEBIT removes it (ink, -). A FAILED line moved no money, so its
 * amount is struck through and the reason from the backend ("FAILED: ...") is shown under the title.
 */
function titleOf(t: Transaction): string {
  if (t.type === 'TRANSFER') {
    const other = t.counterpartyAccountNumber ? groupAccountNumber(t.counterpartyAccountNumber) : 'another account'
    return t.direction === 'DEBIT' ? `Transfer to ${other}` : `Transfer from ${other}`
  }
  return t.type === 'DEPOSIT' ? 'Deposit' : 'Withdrawal'
}

function noteOf(t: Transaction): string | null {
  if (t.status === 'FAILED') return (t.description ?? '').replace(/^FAILED:\s*/, '') || 'Transaction failed'
  const generic = ['Deposit', 'Withdrawal', 'Transfer']
  return t.description && !generic.includes(t.description) ? t.description : null
}

export function TransactionList({ transactions, compact = false }: { transactions: Transaction[]; compact?: boolean }) {
  return (
    <div>
      {!compact && (
        <div className="hidden grid-cols-[1fr_7rem_8rem_8rem] gap-4 border-b border-line px-1 pb-2 text-xs font-semibold uppercase tracking-wide text-faint md:grid">
          <span>Transaction</span>
          <span>Status</span>
          <span className="text-right">Amount</span>
          <span className="text-right">Balance</span>
        </div>
      )}
      <ul className="divide-y divide-line">
        {transactions.map((t) => (
          <TransactionRow key={`${t.transactionRef}-${t.direction}-${t.createdAt}`} t={t} compact={compact} />
        ))}
      </ul>
    </div>
  )
}

function TransactionRow({ t, compact }: { t: Transaction; compact: boolean }) {
  const failed = t.status === 'FAILED'
  const credit = t.direction === 'CREDIT'
  const note = noteOf(t)
  const iconTone = failed
    ? 'bg-rose-500/12 text-rose-600 dark:text-rose-300'
    : credit
      ? 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-300'
      : 'bg-brand-500/10 text-brand-600 dark:text-brand-300'
  const icon = t.type === 'TRANSFER' ? 'swap' : credit ? 'arrowDown' : 'arrowUp'

  return (
    <li className={`grid items-center gap-x-4 gap-y-1 py-3.5 ${compact ? 'grid-cols-[auto_1fr_auto]' : 'grid-cols-[auto_1fr_auto] md:grid-cols-[auto_1fr_7rem_8rem_8rem]'}`}>
      <span className={`grid size-10 place-items-center rounded-xl ${iconTone}`}>
        <Icon name={failed ? 'alert' : icon} className="size-5" />
      </span>

      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-ink">{titleOf(t)}</p>
        <p className="truncate text-xs text-muted">
          {formatDate(t.createdAt)} · {formatTime(t.createdAt)}
          {note && <span className={failed ? ' text-rose-600 dark:text-rose-300' : ''}> · {note}</span>}
        </p>
      </div>

      {!compact && (
        <div className="hidden md:block">
          <TxStatusBadge status={t.status} />
        </div>
      )}

      <p
        className={`text-right text-sm font-semibold tabular ${
          failed ? 'text-faint line-through' : credit ? 'text-emerald-600 dark:text-emerald-300' : 'text-ink'
        }`}
      >
        {credit ? '+' : '−'}
        {formatMoney(t.amount)}
        {!compact && failed && <span className="block text-[11px] font-medium no-underline md:hidden">Failed</span>}
      </p>

      {!compact && <p className="hidden text-right text-sm text-muted tabular md:block">{formatMoney(t.balanceAfter)}</p>}
    </li>
  )
}
