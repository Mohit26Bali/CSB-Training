import { useState, type FormEvent } from 'react'
import { errorMessage, fieldErrorsOf } from '../../api/client'
import { useDeposit, useWithdraw } from '../../api/queries'
import type { Account } from '../../api/types'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { Button, Field, Notice } from '../../components/ui'
import { useIdempotencyKey } from '../../hooks'
import { formatMoney, groupAccountNumber, titleCase } from '../../lib/format'
import { parseAmount } from '../../lib/validation'

/**
 * Deposit / withdraw dialog for one account. Opened from pages/customer/DashboardPage, AccountsPage and
 * AccountDetailPage by passing the account (null = closed).
 *
 * Safety: each submit carries an Idempotency-Key (hooks.ts#useIdempotencyKey). Pressing the button twice or
 * retrying after a network error therefore cannot move money twice - the backend replays the first result. The key
 * is renewed whenever the person edits the form, because an edit means a different request.
 */
const quickAmounts = [500, 1000, 5000, 10000]

type Mode = 'deposit' | 'withdraw'

export function MoneyModal({ mode, account, onClose }: { mode: Mode; account: Account | null; onClose: () => void }) {
  const title = mode === 'deposit' ? 'Deposit money' : 'Withdraw money'
  return (
    <Modal open={account !== null} title={title} onClose={onClose}>
      {account && <MoneyForm key={`${mode}-${account.accountNumber}`} mode={mode} account={account} onClose={onClose} />}
    </Modal>
  )
}

function MoneyForm({ mode, account, onClose }: { mode: Mode; account: Account; onClose: () => void }) {
  const toast = useToast()
  const deposit = useDeposit(account.accountNumber)
  const withdraw = useWithdraw(account.accountNumber)
  const mutation = mode === 'deposit' ? deposit : withdraw
  const { key, renew } = useIdempotencyKey()

  const [amountText, setAmountText] = useState('')
  const [description, setDescription] = useState('')
  const [amountError, setAmountError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)

  const parsed = parseAmount(amountText)
  const overdraw = mode === 'withdraw' && parsed.ok && parsed.value > account.balance

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    if (!parsed.ok) return setAmountError(parsed.error)
    if (overdraw) return setAmountError(`Amount exceeds your available balance of ${formatMoney(account.balance)}`)
    setAmountError(undefined)
    try {
      const result = await mutation.mutateAsync({
        input: { amount: parsed.value, description: description.trim() || undefined },
        key: key(),
      })
      renew()
      toast.success(
        `${formatMoney(parsed.value)} ${mode === 'deposit' ? 'deposited' : 'withdrawn'}. New balance ${formatMoney(result.balanceAfter)}`,
      )
      onClose()
    } catch (err) {
      const server = fieldErrorsOf(err)
      if (server.amount) setAmountError(server.amount)
      else setFormError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <div className="flex items-center justify-between rounded-2xl bg-surface-2 p-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-faint">
            {mode === 'deposit' ? 'To' : 'From'} · {titleCase(account.accountType)}
          </p>
          <p className="mt-0.5 font-mono text-sm tracking-wider text-ink">{groupAccountNumber(account.accountNumber)}</p>
        </div>
        <div className="text-right">
          <p className="text-xs text-faint">Available balance</p>
          <p className="text-sm font-semibold text-ink tabular">{formatMoney(account.balance)}</p>
        </div>
      </div>

      {formError && <Notice tone="error">{formError}</Notice>}

      <Field
        label="Amount"
        prefix="₹"
        inputMode="decimal"
        placeholder="0.00"
        value={amountText}
        onChange={(e) => {
          setAmountText(e.target.value)
          setAmountError(undefined)
          renew()
        }}
        error={amountError}
        autoComplete="off"
      />

      <div className="flex flex-wrap gap-2">
        {quickAmounts.map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setAmountText(String(value))
              setAmountError(undefined)
              renew()
            }}
            className="rounded-full border border-line px-3 py-1 text-xs font-semibold text-muted transition-colors hover:border-brand-500 hover:text-brand-600 dark:hover:text-brand-300"
          >
            + {formatMoney(value).replace('.00', '')}
          </button>
        ))}
      </div>

      <Field
        label="Note (optional)"
        value={description}
        maxLength={200}
        onChange={(e) => {
          setDescription(e.target.value)
          renew()
        }}
        placeholder={mode === 'deposit' ? 'e.g. Salary' : 'e.g. Rent'}
      />

      <div className="flex gap-3 pt-1">
        <Button variant="secondary" className="flex-1" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" loading={mutation.isPending} variant={mode === 'deposit' ? 'success' : 'primary'} className="flex-1">
          <Icon name={mode === 'deposit' ? 'arrowDown' : 'arrowUp'} className="size-4" />
          {mode === 'deposit' ? 'Deposit' : 'Withdraw'}
        </Button>
      </div>
    </form>
  )
}
