import type { Purchase } from '../api/types'
import { formatDate, formatMoney, Tag } from './ui'

/** The item, quantity, cost and the trail of what has happened to a request. Shared by the employee and manager lists. */
export function RequestDetails({ r }: { r: Purchase }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <p className="font-medium text-slate-900">{r.itemName}</p>
        <Tag tone="green">{r.categoryLabel}</Tag>
      </div>
      <p className="mt-1 text-sm text-slate-700">
        {r.quantity} × {formatMoney(r.estimatedUnitCost)} = <strong>{formatMoney(r.estimatedTotal)}</strong>
      </p>
      <p className="mt-1 text-sm text-slate-600">{r.justification}</p>
      <ul className="mt-2 space-y-0.5 text-xs text-slate-500">
        <li>Requested {formatDate(r.submittedAt)}</li>
        {r.decidedBy && r.status !== 'REJECTED' && <li>Approved by {r.decidedBy} on {formatDate(r.decidedAt)}</li>}
        {r.orderedAt && (
          <li>
            Ordered {formatDate(r.orderedAt)}
            {r.orderReference && <> · reference {r.orderReference}</>}
          </li>
        )}
        {r.deliveredAt && <li>Delivered {formatDate(r.deliveredAt)}</li>}
      </ul>
      {r.status === 'REJECTED' && (
        <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
          <span className="font-medium">Rejected by {r.decidedBy}:</span> {r.rejectionReason}
        </p>
      )}
    </>
  )
}
