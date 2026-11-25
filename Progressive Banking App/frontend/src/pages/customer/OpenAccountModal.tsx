import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { errorMessage, fieldErrorsOf } from '../../api/client'
import { useCreateAccount } from '../../api/queries'
import type { AccountType } from '../../api/types'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { Button, Field, Notice } from '../../components/ui'
import { formatMoney, groupAccountNumber } from '../../lib/format'
import { parseAmount } from '../../lib/validation'

/**
 * "Open a new account" dialog (POST /api/accounts). Opened from pages/customer/DashboardPage and AccountsPage.
 * The owner is not sent: the backend takes it from the login. After success it goes to the new account's page.
 * The optional opening deposit is recorded by the backend as the first DEPOSIT in the account's history.
 */
const types: { value: AccountType; title: string; text: string; icon: 'wallet' | 'building' }[] = [
  { value: 'SAVINGS', title: 'Savings account', text: 'For everyday personal banking.', icon: 'wallet' },
  { value: 'CURRENT', title: 'Current account', text: 'For business-style transactions.', icon: 'building' },
]

export function OpenAccountModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} title="Open a new account" description="Choose the account type and, if you like, add an opening deposit." onClose={onClose}>
      {open && <OpenAccountForm onClose={onClose} />}
    </Modal>
  )
}

function OpenAccountForm({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const create = useCreateAccount()
  const [type, setType] = useState<AccountType>('SAVINGS')
  const [depositText, setDepositText] = useState('')
  const [depositError, setDepositError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    let initialDeposit = 0
    if (depositText.trim()) {
      const parsed = parseAmount(depositText)
      if (!parsed.ok) return setDepositError(parsed.error)
      initialDeposit = parsed.value
    }
    try {
      const account = await create.mutateAsync({ accountType: type, initialDeposit })
      toast.success(`Account ${groupAccountNumber(account.accountNumber)} opened${initialDeposit ? ` with ${formatMoney(initialDeposit)}` : ''}`)
      onClose()
      navigate(`/accounts/${account.accountNumber}`)
    } catch (err) {
      const server = fieldErrorsOf(err)
      if (server.initialDeposit) setDepositError(server.initialDeposit)
      else setFormError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError && <Notice tone="error">{formError}</Notice>}

      <div role="radiogroup" aria-label="Account type" className="grid gap-3 sm:grid-cols-2">
        {types.map((option) => {
          const selected = type === option.value
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => setType(option.value)}
              className={`rounded-2xl border p-4 text-left transition-all ${
                selected ? 'border-brand-500 bg-brand-500/8 ring-4 ring-brand-500/12' : 'border-line hover:border-brand-300'
              }`}
            >
              <span className="flex items-center justify-between">
                <span className={`grid size-9 place-items-center rounded-xl ${selected ? 'bg-brand-600 text-white' : 'bg-surface-2 text-muted'}`}>
                  <Icon name={option.icon} className="size-5" />
                </span>
                {selected && <Icon name="checkCircle" className="size-5 text-brand-600 dark:text-brand-300" />}
              </span>
              <span className="mt-3 block text-sm font-semibold text-ink">{option.title}</span>
              <span className="mt-0.5 block text-xs text-muted">{option.text}</span>
            </button>
          )
        })}
      </div>

      <Field
        label="Opening deposit (optional)"
        prefix="₹"
        inputMode="decimal"
        placeholder="0.00"
        value={depositText}
        onChange={(e) => {
          setDepositText(e.target.value)
          setDepositError(undefined)
        }}
        error={depositError}
        autoComplete="off"
      />

      <div className="flex gap-3 pt-1">
        <Button variant="secondary" className="flex-1" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" loading={create.isPending} className="flex-1">
          Open account
        </Button>
      </div>
    </form>
  )
}
