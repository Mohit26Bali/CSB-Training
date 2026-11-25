import { useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage, fieldErrorsOf } from '../../api/client'
import { useMyAccounts, useTransfer } from '../../api/queries'
import type { Account, TransferReceipt } from '../../api/types'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorState, Field, LoadingState, Notice, PageHeader, Segmented, SelectField } from '../../components/ui'
import { useIdempotencyKey } from '../../hooks'
import { formatDateTime, formatMoney, groupAccountNumber, titleCase } from '../../lib/format'
import { parseAmount } from '../../lib/validation'

/**
 * Fund transfer (route /transfer, optionally ?from=<accountNumber>). Three steps: fill the form -> review in a
 * confirmation dialog -> receipt. The destination is either one of the customer's own accounts or any account number.
 *
 * Validation here mirrors the backend rules (amount > 0, source != destination, enough balance) for quick feedback;
 * the backend re-checks all of them plus ownership and account status, and its message is shown if it refuses.
 * The submit carries an Idempotency-Key (hooks.ts) so a double click or retry moves the money only once.
 */
type Destination = 'own' | 'other'

export function TransferPage() {
  const accounts = useMyAccounts()
  if (accounts.isLoading) return <LoadingState label="Loading your accounts…" />
  if (accounts.isError) return <ErrorState message={errorMessage(accounts.error)} onRetry={() => accounts.refetch()} />
  return <TransferFlow accounts={accounts.data ?? []} />
}

function TransferFlow({ accounts }: { accounts: Account[] }) {
  const toast = useToast()
  const [params] = useSearchParams()
  const transfer = useTransfer()
  const { key, renew } = useIdempotencyKey()

  const active = useMemo(() => accounts.filter((a) => a.status === 'ACTIVE'), [accounts])
  const requested = params.get('from')
  const [fromNo, setFromNo] = useState(active.find((a) => a.accountNumber === requested)?.accountNumber ?? active[0]?.accountNumber ?? '')
  const others = active.filter((a) => a.accountNumber !== fromNo)
  const [destination, setDestination] = useState<Destination>('other')
  const [toOwn, setToOwn] = useState('')
  const [toOther, setToOther] = useState('')
  const [amountText, setAmountText] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<{ to?: string; amount?: string }>({})
  const [reviewing, setReviewing] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [receipt, setReceipt] = useState<TransferReceipt | null>(null)

  const from = active.find((a) => a.accountNumber === fromNo)
  const toNumber = destination === 'own' ? (toOwn || others[0]?.accountNumber || '') : toOther.replace(/\s/g, '')
  const parsed = parseAmount(amountText)

  if (active.length === 0) {
    return (
      <div>
        <PageHeader title="Transfer money" />
        <EmptyState
          icon="wallet"
          title="You need an active account to transfer money"
          hint={accounts.length ? 'Your accounts are not active right now. Please contact the bank.' : 'Open an account first.'}
          action={<Link to="/accounts" className="font-semibold text-brand-600 hover:underline dark:text-brand-300">Go to my accounts</Link>}
        />
      </div>
    )
  }

  const touched = () => {
    renew()
    setServerError(null)
  }

  function review(e: FormEvent) {
    e.preventDefault()
    const found: { to?: string; amount?: string } = {}
    if (!/^\d{10}$/.test(toNumber)) found.to = 'Enter a valid 10-digit account number'
    else if (toNumber === fromNo) found.to = 'Source and destination accounts must be different'
    if (!parsed.ok) found.amount = parsed.error
    else if (from && parsed.value > from.balance) found.amount = `Amount exceeds your available balance of ${formatMoney(from.balance)}`
    setErrors(found)
    if (Object.keys(found).length === 0) {
      setServerError(null)
      setReviewing(true)
    }
  }

  async function confirm() {
    if (!from || !parsed.ok) return
    setServerError(null)
    try {
      const result = await transfer.mutateAsync({
        input: { fromAccount: from.accountNumber, toAccount: toNumber, amount: parsed.value, description: note.trim() || undefined },
        key: key(),
      })
      renew()
      setReceipt(result)
      setReviewing(false)
      toast.success('Transfer successful')
    } catch (err) {
      const server = fieldErrorsOf(err)
      if (server.toAccount || server.amount) {
        setErrors({ to: server.toAccount, amount: server.amount })
        setReviewing(false)
      } else {
        setServerError(errorMessage(err))
      }
    }
  }

  function reset() {
    setReceipt(null)
    setAmountText('')
    setNote('')
    setToOther('')
    setErrors({})
  }

  if (receipt) return <Receipt receipt={receipt} onAgain={reset} />

  return (
    <div>
      <PageHeader title="Transfer money" subtitle="Send money instantly to any account at Crestline Bank." />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Card>
          <form onSubmit={review} className="space-y-5" noValidate>
            <SelectField
              label="From"
              value={fromNo}
              onChange={(e) => {
                setFromNo(e.target.value)
                setToOwn('')
                touched()
              }}
            >
              {active.map((a) => (
                <option key={a.accountNumber} value={a.accountNumber}>
                  {titleCase(a.accountType)} · {groupAccountNumber(a.accountNumber)} · {formatMoney(a.balance)}
                </option>
              ))}
            </SelectField>

            <div>
              <p className="mb-1.5 text-sm font-medium text-ink">To</p>
              <Segmented<Destination>
                label="Destination type"
                value={destination}
                onChange={(value) => {
                  setDestination(value)
                  setErrors((e) => ({ ...e, to: undefined }))
                  touched()
                }}
                options={[
                  { value: 'other', label: 'Another account' },
                  { value: 'own', label: 'My accounts' },
                ]}
              />
              <div className="mt-3">
                {destination === 'own' ? (
                  others.length > 0 ? (
                    <SelectField
                      label="Destination account"
                      value={toOwn || others[0]!.accountNumber}
                      onChange={(e) => {
                        setToOwn(e.target.value)
                        touched()
                      }}
                    >
                      {others.map((a) => (
                        <option key={a.accountNumber} value={a.accountNumber}>
                          {titleCase(a.accountType)} · {groupAccountNumber(a.accountNumber)}
                        </option>
                      ))}
                    </SelectField>
                  ) : (
                    <Notice tone="info">You have no other active account. Choose “Another account” or open a new account.</Notice>
                  )
                ) : (
                  <Field
                    label="Recipient account number"
                    inputMode="numeric"
                    maxLength={12}
                    placeholder="10-digit account number"
                    value={toOther}
                    onChange={(e) => {
                      setToOther(e.target.value.replace(/[^\d\s]/g, ''))
                      setErrors((v) => ({ ...v, to: undefined }))
                      touched()
                    }}
                    error={errors.to}
                    autoComplete="off"
                  />
                )}
                {destination === 'own' && errors.to && <p className="mt-1.5 text-xs text-rose-600 dark:text-rose-400">{errors.to}</p>}
              </div>
            </div>

            <Field
              label="Amount"
              prefix="₹"
              inputMode="decimal"
              placeholder="0.00"
              value={amountText}
              onChange={(e) => {
                setAmountText(e.target.value)
                setErrors((v) => ({ ...v, amount: undefined }))
                touched()
              }}
              error={errors.amount}
              hint={from ? `Available: ${formatMoney(from.balance)}` : undefined}
              autoComplete="off"
            />

            <Field
              label="Note (optional)"
              value={note}
              maxLength={200}
              onChange={(e) => {
                setNote(e.target.value)
                touched()
              }}
              placeholder="e.g. Rent for October"
            />

            <Button type="submit" className="w-full py-3">
              Review transfer <Icon name="arrowRight" className="size-4" />
            </Button>
          </form>
        </Card>

        <aside className="space-y-4">
          <Card className="bg-linear-to-br from-brand-900 to-brand-700 !border-0 text-white">
            <Icon name="shield" className="size-7 text-mint-400" />
            <h2 className="mt-3 font-semibold">Safe to retry</h2>
            <p className="mt-1.5 text-sm leading-relaxed text-white/75">
              Every transfer is protected against duplicates. If your connection drops, press Confirm again; the money will move only once.
            </p>
          </Card>
          <Card>
            <h2 className="text-sm font-semibold text-ink">Good to know</h2>
            <ul className="mt-3 space-y-2.5 text-sm text-muted">
              {[
                'Both accounts must be active.',
                'You can’t transfer to the same account.',
                'The amount can’t exceed your available balance.',
                'Every transfer appears in both accounts’ history.',
              ].map((text) => (
                <li key={text} className="flex gap-2.5">
                  <Icon name="check" className="mt-0.5 size-4 shrink-0 text-mint-600" /> {text}
                </li>
              ))}
            </ul>
          </Card>
        </aside>
      </div>

      <Modal open={reviewing} title="Confirm transfer" description="Please check the details before you send." onClose={() => !transfer.isPending && setReviewing(false)}>
        {from && parsed.ok && (
          <div className="space-y-4">
            {serverError && <Notice tone="error">{serverError}</Notice>}
            <div className="rounded-2xl bg-surface-2 p-5 text-center">
              <p className="text-xs font-medium uppercase tracking-wide text-faint">You are sending</p>
              <p className="mt-1 text-3xl font-semibold tracking-tight text-ink tabular">{formatMoney(parsed.value)}</p>
            </div>
            <dl className="divide-y divide-line text-sm">
              <Row label="From" value={`${titleCase(from.accountType)} · ${groupAccountNumber(from.accountNumber)}`} />
              <Row label="To" value={groupAccountNumber(toNumber)} mono />
              {note.trim() && <Row label="Note" value={note.trim()} />}
              <Row label="Balance after" value={formatMoney(from.balance - parsed.value)} />
            </dl>
            <div className="flex gap-3 pt-1">
              <Button variant="secondary" className="flex-1" disabled={transfer.isPending} onClick={() => setReviewing(false)}>
                Edit
              </Button>
              <Button className="flex-1" loading={transfer.isPending} onClick={confirm}>
                Confirm & send
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

function Row({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4 py-2.5">
      <dt className="text-muted">{label}</dt>
      <dd className={`text-right font-medium text-ink ${mono ? 'font-mono tracking-wider' : ''}`}>{value}</dd>
    </div>
  )
}

function Receipt({ receipt, onAgain }: { receipt: TransferReceipt; onAgain: () => void }) {
  const toast = useToast()
  async function copy() {
    try {
      await navigator.clipboard.writeText(receipt.transactionRef)
      toast.success('Reference copied')
    } catch {
      toast.error('Could not copy the reference')
    }
  }
  return (
    <div className="mx-auto max-w-lg">
      <Card className="pop-in text-center">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-300">
          <Icon name="checkCircle" className="size-9" />
        </span>
        <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Transfer successful</h1>
        <p className="mt-1 text-3xl font-semibold text-ink tabular">{formatMoney(receipt.amount)}</p>
        <p className="mt-1 text-sm text-muted">sent on {formatDateTime(receipt.createdAt)}</p>

        <dl className="mt-6 divide-y divide-line rounded-2xl border border-line text-left text-sm">
          <div className="flex justify-between gap-4 px-4 py-3">
            <dt className="text-muted">From</dt>
            <dd className="font-mono font-medium tracking-wider text-ink">{groupAccountNumber(receipt.fromAccount)}</dd>
          </div>
          <div className="flex justify-between gap-4 px-4 py-3">
            <dt className="text-muted">To</dt>
            <dd className="font-mono font-medium tracking-wider text-ink">{groupAccountNumber(receipt.toAccount)}</dd>
          </div>
          <div className="flex justify-between gap-4 px-4 py-3">
            <dt className="text-muted">New balance</dt>
            <dd className="font-medium text-ink tabular">{formatMoney(receipt.fromAccountBalanceAfter)}</dd>
          </div>
          <div className="flex items-center justify-between gap-4 px-4 py-3">
            <dt className="text-muted">Reference</dt>
            <dd className="flex min-w-0 items-center gap-1.5">
              <span className="truncate font-mono text-xs text-ink">{receipt.transactionRef}</span>
              <button type="button" onClick={copy} aria-label="Copy reference" className="grid size-7 shrink-0 place-items-center rounded-lg text-faint hover:bg-brand-500/10 hover:text-ink">
                <Icon name="copy" className="size-4" />
              </button>
            </dd>
          </div>
        </dl>

        <div className="mt-6 flex gap-3">
          <Link
            to="/transactions"
            className="inline-flex flex-1 items-center justify-center rounded-xl border border-line bg-surface px-4 py-2.5 text-sm font-semibold text-ink transition-colors hover:bg-surface-2"
          >
            View transactions
          </Link>
          <Button className="flex-1" onClick={onAgain}>
            New transfer
          </Button>
        </div>
      </Card>
    </div>
  )
}
