/**
 * Normalises an Axios error into { message, fields }.
 * The backend answers with RFC 9457 ProblemDetail: { title, status, detail, errors? }.
 */
export function describeError(error) {
  if (!error.response) {
    return { message: 'Cannot reach the server. Is the backend running?', fields: {} }
  }
  const data = error.response.data
  return {
    message: data?.detail || data?.title || `Request failed (${error.response.status})`,
    fields: data?.errors ?? {},
  }
}
