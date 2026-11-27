import axios from 'axios'

/**
 * Turns a failed Run or Submit request into a message for the learner, or null when the request was cancelled on purpose (leaving the page).
 *
 * `outcomeUnknown` is true when the browser did not get an answer (network error or timeout): the server may or may not have judged and
 * stored the submission, so the page must NOT show any verdict and must tell the learner to check the history.
 */
export function describeRequestError(error, action) {
  if (axios.isCancel(error)) return null
  const what = action === 'submit' ? 'submission' : 'sample run'
  if (!error.response) {
    const timedOut = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    return {
      kind: timedOut ? 'timeout' : 'network',
      status: null,
      outcomeUnknown: true,
      message:
        (timedOut ? 'The server did not answer in time.' : 'Could not reach the server.') +
        ` No result was received, so none is shown.${
          action === 'submit' ? ' If the submission reached the server it will appear in your submission history; check it before submitting again.' : ''
        } Your code is unchanged.`,
    }
  }
  const status = error.response.status
  const data = error.response.data
  const detail = data?.detail && data.detail !== 'Validation failed' ? data.detail : null
  const fieldProblems = data?.errors ? Object.values(data.errors).join(' ') : ''
  const base = { status, outcomeUnknown: false }
  switch (status) {
    case 400:
      return { ...base, kind: 'invalid', message: `The server refused this ${what}: ${detail || fieldProblems || 'the source is not acceptable'}. Your code is unchanged.` }
    case 401:
      return { ...base, kind: 'unauthorized', message: 'Your session has expired. Log in again to continue. Code that is not saved in a submission is lost when you leave this page.' }
    case 404:
      return { ...base, kind: 'not-found', message: 'This problem is no longer available.' }
    case 409:
      return { ...base, kind: 'busy', message: 'You already have a run or submission in progress. Wait for it to finish, then try again. Your code is unchanged.' }
    case 429:
      return { ...base, kind: 'capacity', message: 'The code runner is busy right now. Try again in a moment. Your code is unchanged.' }
    case 503:
      return { ...base, kind: 'unavailable', message: 'Code execution is not available right now. Nothing was judged and nothing was recorded. Your code is unchanged.' }
    default:
      return { ...base, kind: 'error', message: `Something went wrong on the server (${status}). No result was received. Your code is unchanged.` }
  }
}

/** Message for a failed read (problem, history, progress). */
export function describeLoadError(error) {
  if (!error.response) return { status: null, message: 'Cannot reach the server. Is the backend running?' }
  const status = error.response.status
  if (status === 404) return { status, message: 'Not found.' }
  if (status === 503) return { status, message: 'Coding practice is not available right now.' }
  return { status, message: error.response.data?.detail || error.response.data?.title || `Request failed (${status})` }
}
