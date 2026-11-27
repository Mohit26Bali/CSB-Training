import client from '../../api/client'
import { getToken } from '../../auth/tokenStorage'
import { tamperToken } from '../schema/scenario'

export async function getMeta() {
  return (await client.get('/api/lab/meta')).data
}

export async function getCodeReferences() {
  const { data } = await client.get('/api/lab/code-references')
  return Object.fromEntries(data.map((ref) => [ref.id, ref]))
}

export async function getCatalog() {
  return (await client.get('/api/lab/lifecycle/catalog')).data
}

export async function getTrace(id) {
  return (await client.get(`/api/lab/traces/${id}`)).data
}

/**
 * Step 1 of tracing: an authenticated request (the learner's real token, through the shared client) obtains a
 * single-use ticket that makes the learner the owner of the next trace. The traced request itself may be anonymous or
 * carry a deliberately bad token, so ownership cannot come from it.
 */
export async function requestTraceTicket() {
  return (await client.post('/api/lab/traces/tickets')).data.ticket
}

/**
 * Sends ONE real GET request with the trace ticket and returns what the browser saw plus the server-assembled trace
 * (readable only by the ticket's owner). `authorization` is the complete Authorization header value, or null for none.
 * The traced request uses fetch (not the shared Axios client) on purpose: a deliberate 401 must not trigger the app's
 * global "session expired" logout.
 */
export async function tracedFetch(url, authorization) {
  const ticket = await requestTraceTicket()
  const headers = { Accept: 'application/json', 'X-SpringLab-Trace': ticket }
  if (authorization) headers.Authorization = authorization

  const started = performance.now()
  const response = await fetch(url, { method: 'GET', headers, cache: 'no-store' })
  const body = await response.arrayBuffer()
  const elapsedMs = performance.now() - started

  const traceId = response.headers.get('x-springlab-trace-id')
  if (!traceId) throw new Error('The server did not return a trace id for this request.')
  const trace = await getTrace(traceId)
  return {
    browser: {
      status: response.status,
      contentType: response.headers.get('content-type') ?? '(none)',
      bytes: body.byteLength,
      elapsedMs: Math.round(elapsedMs),
    },
    trace,
  }
}

/**
 * REST Request Lifecycle experiments. `variant` only changes THIS request:
 *   AS_IS          the learner's real token
 *   NO_TOKEN       no Authorization header
 *   TAMPERED_TOKEN the learner's token with one character of its signature altered
 */
export async function runTracedCall(target, variant) {
  const token = getToken()
  let authorization = null
  if (variant !== 'NO_TOKEN' && token) {
    authorization = `Bearer ${variant === 'TAMPERED_TOKEN' ? tamperToken(token) : token}`
  }
  const url = target.query ? `${target.path}?${target.query}` : target.path
  return { target, variant, ...(await tracedFetch(url, authorization)) }
}
