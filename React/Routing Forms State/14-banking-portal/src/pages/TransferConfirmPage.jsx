import { useEffect, useRef } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { rupees } from '../format.js'
import { useBank } from '../state/BankContext.jsx'

/** The challenge: one more look before the money moves. Nothing changes until Confirm. */
function TransferConfirmPage() {
  const { pendingTransfer, findAccount, completeTransfer, cancelTransfer, lastTransferId } = useBank()
  const navigate = useNavigate()
  const leaving = useRef(null)                      // 'confirm' or 'cancel' once a button was pressed

  // After COMPLETE_TRANSFER the reducer stores the new id: show that transaction.
  useEffect(() => {
    if (leaving.current === 'confirm' && lastTransferId) {
      navigate(`/transactions/${lastTransferId}`, { replace: true, state: { message: 'Transfer completed successfully.' } })
    }
  }, [lastTransferId, navigate])

  // Opened directly with nothing to confirm: back to the form. (Not while leaving on purpose.)
  if (!pendingTransfer && !leaving.current) return <Navigate to="/transfer" replace />
  if (!pendingTransfer) return null

  const from = findAccount(pendingTransfer.fromAccountId)

  const confirm = () => {
    leaving.current = 'confirm'
    completeTransfer()
  }

  const cancel = () => {
    leaving.current = 'cancel'
    cancelTransfer()
    navigate('/dashboard')
  }

  return (
    <section className="card narrow">
      <h1 className="page-title">Confirm transfer</h1>
      <dl className="details">
        <dt>From</dt><dd>{from.name} ({from.id})</dd>
        <dt>To</dt><dd>{pendingTransfer.toLabel}</dd>
        <dt>Amount</dt><dd className="strong">{rupees(pendingTransfer.amount)}</dd>
        {pendingTransfer.remark && <><dt>Remark</dt><dd>{pendingTransfer.remark}</dd></>}
        <dt>Balance after</dt><dd>{rupees(from.balance - pendingTransfer.amount)}</dd>
      </dl>
      <div className="form__actions">
        <button type="button" className="button button--primary" onClick={confirm}>Confirm transfer</button>
        <button type="button" className="button" onClick={() => navigate('/transfer')}>Edit</button>
        <button type="button" className="button button--danger" onClick={cancel}>Cancel</button>
      </div>
    </section>
  )
}

export default TransferConfirmPage
