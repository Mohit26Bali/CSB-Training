import type { Claim } from '../api/types'
import { formatDate, Tag } from './ui'

const priorityTone: Record<string, string> = {
  HIGH: 'bg-rose-100 text-rose-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  LOW: 'bg-slate-100 text-slate-600',
}

export function PriorityBadge({ priority }: { priority: string }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${priorityTone[priority] ?? priorityTone.LOW}`}>
      {priority.charAt(0) + priority.slice(1).toLowerCase()} priority
    </span>
  )
}

/** The product, the problem and the trail of what has happened to a claim. Shared by the customer and agent lists. */
export function ClaimDetails({ c, showCustomer }: { c: Claim; showCustomer?: boolean }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <p className="font-medium text-slate-900">
          {c.brand} {c.productName}
        </p>
        <Tag tone="green">{c.categoryLabel}</Tag>
        <PriorityBadge priority={c.priority} />
      </div>
      <p className="mt-0.5 text-xs text-slate-500">
        Serial {c.serialNumber} · warranty until {c.warrantyEnd}
        {showCustomer && (
          <>
            {' '}
            · {c.customerName} ({c.customerUsername})
          </>
        )}
      </p>
      <p className="mt-2 text-sm text-slate-700">“{c.issueDescription}”</p>
      <ul className="mt-2 space-y-0.5 text-xs text-slate-500">
        <li>Filed {formatDate(c.createdAt)}</li>
        {c.decidedBy && c.status !== 'REJECTED' && <li>Approved by {c.decidedBy} on {formatDate(c.decidedAt)}</li>}
        {c.repairStartedAt && <li>Repair started {formatDate(c.repairStartedAt)}</li>}
        {c.completedAt && <li>Completed {formatDate(c.completedAt)}</li>}
      </ul>
      {c.status === 'REJECTED' && (
        <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
          <span className="font-medium">Rejected by {c.decidedBy}:</span> {c.rejectionReason}
        </p>
      )}
      {c.status === 'COMPLETED' && c.resolutionNote && (
        <p className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          <span className="font-medium">Resolution:</span> {c.resolutionNote}
        </p>
      )}
    </>
  )
}
