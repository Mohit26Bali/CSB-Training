import ModeBadge from './ModeBadge'

/**
 * The step list is both the mobile-friendly alternative to the diagram and a keyboard-accessible index of the whole
 * walkthrough. The current step is exposed with aria-current="step".
 */
export default function StepList({ steps, currentIndex, onSelect }) {
  return (
    <ol className="space-y-1" aria-label="Steps">
      {steps.map((step, i) => {
        const current = i === currentIndex
        return (
          <li key={step.id}>
            <button
              type="button"
              onClick={() => onSelect(i)}
              aria-current={current ? 'step' : undefined}
              className={`flex w-full items-start gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                current ? 'border-accent bg-accent-soft font-medium shadow-sm' : 'border-line bg-surface hover:border-line-strong hover:bg-elevated'
              }`}
            >
              <span className={`mt-0.5 w-6 shrink-0 text-right font-mono text-xs ${current ? 'text-accent' : 'text-muted'}`}>{i + 1}</span>
              <span className="flex-1">{step.title}</span>
              <ModeBadge provenance={step.provenance} />
            </button>
          </li>
        )
      })}
    </ol>
  )
}
