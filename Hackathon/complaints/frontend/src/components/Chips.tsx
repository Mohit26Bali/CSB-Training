import type { PriorityName } from '../api/types'

const styles: Record<PriorityName, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-100 text-sky-800',
  HIGH: 'bg-amber-100 text-amber-800',
  URGENT: 'bg-rose-100 text-rose-800',
}

export function PriorityChip({ priority }: { priority: PriorityName }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${styles[priority]}`}>
      <span className="size-1.5 rounded-full bg-current" />
      {priority.charAt(0) + priority.slice(1).toLowerCase()}
    </span>
  )
}

/** Five stars, filled up to the rating. With onPick it becomes a rating input. */
export function Stars({ value, onPick, size = 'size-5' }: { value: number; onPick?: (n: number) => void; size?: string }) {
  return (
    <span className="inline-flex gap-0.5" role={onPick ? 'radiogroup' : 'img'} aria-label={onPick ? 'Rating' : `${value} out of 5`}>
      {[1, 2, 3, 4, 5].map((n) => {
        const star = (
          <svg viewBox="0 0 24 24" className={`${size} ${n <= value ? 'fill-amber-400 text-amber-400' : 'fill-transparent text-slate-300'}`} stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
            <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z" strokeLinejoin="round" />
          </svg>
        )
        return onPick ? (
          <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n} star${n === 1 ? '' : 's'}`} onClick={() => onPick(n)} className="rounded hover:scale-110">
            {star}
          </button>
        ) : (
          <span key={n}>{star}</span>
        )
      })}
    </span>
  )
}
