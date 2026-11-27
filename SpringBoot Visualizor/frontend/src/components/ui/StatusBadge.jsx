const TONES = {
  success: 'bg-success-soft text-success ring-success-line',
  info: 'bg-info-soft text-info ring-info-line',
  brand: 'bg-accent-soft text-accent ring-accent/30',
  warning: 'bg-warning-soft text-warning ring-warning-line',
  danger: 'bg-error-soft text-error ring-error-line',
  neutral: 'bg-subtle text-fg-2 ring-line',
}

/** Small pill for a state ("Available", "Upcoming", a role). Text always carries the meaning; colour only reinforces it. */
export default function StatusBadge({ tone = 'neutral', children, className = '' }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONES[tone]} ${className}`}>
      {children}
    </span>
  )
}
