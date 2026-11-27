import { badgeFor, PROVENANCE } from '../schema/scenario'

const TONES = {
  // Provenance colours have fixed meanings: captured (observed), derived (inferred), conceptual (simulated).
  live: 'border-captured/60 bg-captured-soft text-captured',
  derived: 'border-derived/60 bg-derived-soft text-derived',
  simulated: 'border-dashed border-conceptual/70 bg-conceptual-soft text-conceptual',
}

/**
 * The truthfulness indicator. It is driven ONLY by a step's provenance: CAPTURED is the sole value that can read
 * "LIVE". The label always carries text and an icon (never colour alone) and a screen-reader explanation.
 */
export default function ModeBadge({ provenance }) {
  const badge = badgeFor(provenance)
  return (
    <span
      data-testid="mode-badge"
      data-tone={badge.tone}
      title={badge.hint}
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-semibold ${TONES[badge.tone]}`}
    >
      <span aria-hidden="true">{badge.icon}</span>
      {badge.label}
      <span className="sr-only"> - {badge.hint}</span>
    </span>
  )
}

/** Summary line for a whole scenario: how many steps are observed, inferred or conceptual. */
export function ProvenanceSummary({ steps }) {
  const count = (p) => steps.filter((s) => s.provenance === p).length
  return (
    <p className="text-xs text-fg-2" data-testid="provenance-summary">
      {count(PROVENANCE.CAPTURED)} live, {count(PROVENANCE.DERIVED)} inferred, {count(PROVENANCE.CONCEPTUAL)} simulated
      of {steps.length} steps
    </p>
  )
}
