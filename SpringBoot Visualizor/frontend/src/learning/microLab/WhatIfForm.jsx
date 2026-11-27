import { useId } from 'react'
import { validateWhatIf } from '../schema/microWhatIf'

const control = 'rounded-md border border-line-strong bg-surface px-2 py-1.5 text-sm text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-accent'

/**
 * The What-If controls of one experiment. There is exactly one control per option the server publishes, each shown with the name of the
 * request field it fills ("sent as callTimeoutMillis"), so the learner can see these are the real inputs and nothing else.
 */
export default function WhatIfForm({ experimentId, options, values, onChange, disabled }) {
  const base = useId()
  const errors = validateWhatIf(options, values)
  if (!options?.length) {
    return <p className="text-xs text-muted">This experiment has no What-If controls: it always runs the same way.</p>
  }
  return (
    <fieldset className="space-y-3" disabled={disabled} data-testid={`whatif-${experimentId}`}>
      <legend className="text-xs font-semibold uppercase tracking-wide text-fg-2">What-If controls</legend>
      {options.map((o) => {
        const id = `${base}-${o.key}`
        const error = errors[o.key]
        const describedBy = error ? `${id}-error` : `${id}-key`
        return (
          <div key={o.key} className="space-y-1">
            <label htmlFor={id} className="block text-sm font-medium text-fg">
              {o.label}
            </label>
            {o.kind === 'BOOLEAN' && (
              <select id={id} className={control} value={values[o.key]} onChange={(e) => onChange({ ...values, [o.key]: e.target.value })} aria-describedby={describedBy} aria-invalid={Boolean(error)}>
                <option value="true">Yes</option>
                <option value="false">No</option>
              </select>
            )}
            {o.kind === 'CHOICE' && (
              <select id={id} className={control} value={values[o.key]} onChange={(e) => onChange({ ...values, [o.key]: e.target.value })} aria-describedby={describedBy} aria-invalid={Boolean(error)}>
                {o.choices.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            )}
            {o.kind === 'INT' && (
              <div className="flex flex-wrap items-center gap-2">
                <input
                  id={id}
                  type="number"
                  inputMode="numeric"
                  min={o.min}
                  max={o.max}
                  step={1}
                  className={`${control} w-28`}
                  value={values[o.key]}
                  onChange={(e) => onChange({ ...values, [o.key]: e.target.value })}
                  aria-describedby={describedBy}
                  aria-invalid={Boolean(error)}
                />
                <span className="text-xs text-muted">
                  {o.min} to {o.max}
                </span>
              </div>
            )}
            {error ? (
              <p id={`${id}-error`} role="alert" className="text-xs text-error">
                {error}
              </p>
            ) : (
              <p id={`${id}-key`} className="text-xs text-muted">
                Sent as <code className="rounded bg-code px-1 py-px text-fg">{o.key}</code>, default {o.defaultValue}
              </p>
            )}
          </div>
        )
      })}
    </fieldset>
  )
}
