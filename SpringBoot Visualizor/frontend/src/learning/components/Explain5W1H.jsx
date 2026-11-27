import Icon from '../../components/ui/Icon'
import { EXPLAIN_SECTIONS, validateExplain5W1H } from '../schema/scenario'

/**
 * The mandatory What / Why / Who / Where / When / How explanation. If any section is missing or too thin the
 * component says so instead of rendering a partial lesson (the same validator runs in the test suite).
 */
export default function Explain5W1H({ explain, heading = 'Understand the concept' }) {
  const problems = validateExplain5W1H(explain)
  if (problems.length > 0) {
    return (
      <div role="alert" className="rounded border border-error-line bg-error-soft p-3 text-sm text-error">
        This lesson's 5W1H explanation is incomplete: {problems.join('; ')}.
      </div>
    )
  }
  return (
    <section aria-label={heading} className="space-y-2">
      {EXPLAIN_SECTIONS.map(({ key, title, question }, i) => (
        // Collapsible so a long explanation does not turn the stage into a scroll: the first section (What) is open.
        <details key={key} open={i === 0} data-testid={`5w1h-${key}`} className="group rounded-xl border border-line bg-surface">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 rounded-xl px-4 py-3 text-sm hover:bg-elevated [&::-webkit-details-marker]:hidden">
            <span className="flex flex-wrap items-baseline gap-x-2">
              <span className="rounded bg-accent-soft px-1.5 py-0.5 text-xs font-bold uppercase tracking-wide text-accent">{title}</span>
              <span className="text-fg-2">{question}</span>
            </span>
            <Icon name="chevron" className="h-4 w-4 shrink-0 text-muted transition-transform group-open:rotate-180 motion-reduce:transition-none" />
          </summary>
          <div className="border-t border-line px-4 py-3">
            {key === 'who' ? (
              <ul className="space-y-1 text-sm text-fg-2">
                {explain.who.map((p) => (
                  <li key={p.name}>
                    <span className="font-medium text-fg">{p.name}</span>: {p.role}
                  </li>
                ))}
              </ul>
            ) : key === 'how' ? (
              <ol className="list-decimal space-y-1 pl-5 text-sm text-fg-2">
                {explain.how.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ol>
            ) : (
              <p className="text-sm leading-6 text-fg-2">{explain[key]}</p>
            )}
          </div>
        </details>
      ))}
    </section>
  )
}
