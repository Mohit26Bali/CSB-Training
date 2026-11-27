/**
 * How each verdict the API can return is shown. Labels are words, never only colour. The explanations say only what the verdict means; they do not
 * claim a runtime or memory figure, because the API returns none.
 */
export const VERDICTS = Object.freeze({
  ACCEPTED: { label: 'Accepted', tone: 'success', meaning: 'Every test passed, including the hidden ones. The problem counts as solved.' },
  WRONG_ANSWER: { label: 'Wrong answer', tone: 'danger', meaning: 'Your code compiled and ran, but at least one test failed.' },
  COMPILE_ERROR: { label: 'Compile error', tone: 'warning', meaning: 'Your code did not compile, so no test could run. The compiler output is below; its line numbers refer to your source.' },
  RUNTIME_ERROR: { label: 'Runtime error', tone: 'danger', meaning: 'Your program ended abnormally before the tests finished, for example by calling System.exit.' },
  TIME_LIMIT: { label: 'Time limit exceeded', tone: 'warning', meaning: 'Your code did not finish in time. Look for a loop that never ends.' },
  MEMORY_LIMIT: { label: 'Memory limit exceeded', tone: 'warning', meaning: 'Your code used more memory than the runner allows.' },
  OUTPUT_LIMIT: { label: 'Output limit exceeded', tone: 'warning', meaning: 'Your code printed more than the runner allows.' },
  INVALID_SUBMISSION: { label: 'Source refused', tone: 'warning', meaning: 'The source was refused before it ran (empty, too large or not valid text).' },
  INTERNAL_ERROR: { label: 'Runner problem', tone: 'info', meaning: 'The code runner had a problem. This is not your fault, and it does not count as an attempt.' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', meaning: 'The run was cancelled. It does not count as an attempt.' },
})

/** A verdict this page does not know is shown as itself, never as a success. */
export function verdictInfo(verdict) {
  return VERDICTS[verdict] ?? { label: String(verdict), tone: 'neutral', meaning: 'The server returned a result this page does not recognise.' }
}

/** Test status words (PASS, FAIL, NOT_RUN, ERROR, TIMEOUT) as shown to the learner. */
export const TEST_STATUS = Object.freeze({
  PASS: { label: 'Passed', tone: 'success' },
  FAIL: { label: 'Failed', tone: 'danger' },
  NOT_RUN: { label: 'Not run', tone: 'neutral' },
  ERROR: { label: 'Error', tone: 'danger' },
  TIMEOUT: { label: 'Timed out', tone: 'warning' },
})

export function testStatusInfo(status) {
  return TEST_STATUS[status] ?? { label: String(status), tone: 'neutral' }
}

export const LANGUAGES = Object.freeze({ java17: 'Java 17' })
export const languageLabel = (language) => LANGUAGES[language] ?? language

export function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString()
}
