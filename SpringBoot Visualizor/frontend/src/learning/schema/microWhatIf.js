/**
 * What-If values for one experiment, derived ONLY from the options the server publishes for it (key, kind, bounds, choices, default).
 * The browser never invents an input: a control exists for each server option and nothing else is sent. The server validates again.
 */

export const defaultsOf = (options) => Object.fromEntries((options ?? []).map((o) => [o.key, o.defaultValue]))

/** A map {key: message} of problems; empty means every value is acceptable to the server's rules. */
export function validateWhatIf(options, values) {
  const errors = {}
  for (const o of options ?? []) {
    const v = values?.[o.key]
    if (o.kind === 'BOOLEAN') {
      if (v !== 'true' && v !== 'false') errors[o.key] = 'Choose yes or no.'
    } else if (o.kind === 'INT') {
      const n = Number(v)
      if (v === '' || v == null || !Number.isInteger(n)) errors[o.key] = 'Enter a whole number.'
      else if (n < o.min || n > o.max) errors[o.key] = `Enter a number from ${o.min} to ${o.max}.`
    } else if (!(o.choices ?? []).includes(v)) {
      errors[o.key] = 'Choose one of the listed values.'
    }
  }
  return errors
}

/** The request body's `whatIf`: every option, as the string the server expects. */
export function toWhatIfRequest(options, values) {
  return Object.fromEntries((options ?? []).map((o) => [o.key, String(values?.[o.key] ?? o.defaultValue)]))
}

/** The options whose value differs from the default, for a summary line. */
export function changedOptions(options, values) {
  return (options ?? []).filter((o) => String(values?.[o.key] ?? o.defaultValue) !== o.defaultValue)
}
