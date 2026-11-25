import { useEffect, useState } from 'react'
import type { ProcessingStep } from '../api/types'
import { usePrefersReducedMotion } from '../hooks'
import { Spinner } from './ui'

/**
 * The stages a comment went through (backend service/CommentProcessor): Cleaned, Spam check, Profanity filter, Sentiment
 * analysis, Decision - each with its outcome and a sentence of detail. Shown in two ways:
 *   - {@code animate}: right after posting, the stages appear one after another with a "checking" spinner for the next one, so
 *     the person SEES the processing happen (all at once for people who asked for reduced motion);
 *   - plain: the "How was this processed?" list under any comment, all stages at once.
 * Used by components/CommentForm (the live panel) and components/CommentItem (the disclosure).
 */
const ICON: Record<string, string> = { PASSED: '✓', CHANGED: '✎', FLAGGED: '⚑', HELD: '⏸' }
const TONE: Record<string, string> = { PASSED: 'text-emerald-500', CHANGED: 'text-amber-500', FLAGGED: 'text-amber-500', HELD: 'text-rose-400' }
const STEP_MS = 420

export function ProcessingSteps({ steps, animate = false, onFinished }: { steps: ProcessingStep[]; animate?: boolean; onFinished?: () => void }) {
  const reducedMotion = usePrefersReducedMotion()
  const instant = !animate || reducedMotion
  const [revealed, setRevealed] = useState(instant ? steps.length : 0)
  const shown = instant ? steps.length : revealed

  useEffect(() => {
    if (shown >= steps.length) {
      onFinished?.()
      return
    }
    const timer = window.setTimeout(() => setRevealed(shown + 1), STEP_MS)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shown, steps.length])

  return (
    <ol aria-label="Processing steps" className="space-y-1.5 text-sm">
      {steps.slice(0, shown).map((step) => (
        <li key={step.name} data-step={step.name} data-outcome={step.outcome} className="fade-in flex items-start gap-2.5">
          <span aria-hidden="true" className={`mt-px w-4 shrink-0 text-center font-bold ${TONE[step.outcome] ?? 'text-muted'}`}>
            {ICON[step.outcome] ?? '•'}
          </span>
          <span className="text-ink/90">
            <strong className="font-semibold text-ink">{step.name}</strong>
            <span className="sr-only"> ({step.outcome.toLowerCase()})</span>: {step.detail}
          </span>
        </li>
      ))}
      {shown < steps.length && (
        <li data-testid="processing-next" className="flex items-center gap-2.5 text-muted">
          <Spinner small /> <span>Checking {steps[shown].name.toLowerCase()}…</span>
        </li>
      )}
    </ol>
  )
}
