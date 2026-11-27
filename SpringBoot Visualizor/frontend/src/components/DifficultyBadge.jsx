const STYLES = {
  EASY: 'bg-success-soft text-success',
  MEDIUM: 'bg-warning-soft text-warning',
  HARD: 'bg-error-soft text-error',
}

export default function DifficultyBadge({ difficulty }) {
  if (!difficulty) return null
  return (
    <span className={`rounded px-2 py-0.5 text-xs font-medium ${STYLES[difficulty] ?? 'bg-subtle text-fg-2'}`}>
      {difficulty}
    </span>
  )
}
