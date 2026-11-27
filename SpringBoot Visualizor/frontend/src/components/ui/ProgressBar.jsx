/** A labelled meter. Only render it with a value that comes from real data. */
export default function ProgressBar({ value, label, tone = 'bg-accent' }) {
  const pct = Math.max(0, Math.min(100, Number(value) || 0))
  return (
    <div
      role="meter"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
      aria-label={label}
      className="h-2 overflow-hidden rounded-full bg-subtle"
    >
      <div className={`h-full rounded-full ${tone}`} style={{ width: `${pct}%` }} />
    </div>
  )
}
