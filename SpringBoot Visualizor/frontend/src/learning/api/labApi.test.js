import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/client', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../auth/tokenStorage', () => ({ getToken: vi.fn(() => 'REAL.ACCESS.TOKEN') }))

import client from '../../api/client'
import { requestTraceTicket, runTracedCall, tracedFetch } from './labApi'

const response = (status, headers = {}) => ({
  status,
  headers: { get: (name) => headers[name.toLowerCase()] ?? null },
  arrayBuffer: async () => new ArrayBuffer(12),
})

describe('traced requests: the ticket handshake', () => {
  beforeEach(() => {
    client.post.mockResolvedValue({ data: { ticket: 'TICKET-1', expiresInSeconds: 60 } })
    client.get.mockResolvedValue({ data: { id: 'trace-9', steps: [], sql: [], limitations: [] } })
    globalThis.fetch = vi.fn().mockResolvedValue(response(200, { 'x-springlab-trace-id': 'trace-9', 'content-type': 'application/json' }))
  })
  afterEach(() => vi.clearAllMocks())

  it('obtains a ticket for the signed-in user first, then sends it (not the literal "1") as the trace header', async () => {
    await tracedFetch('/api/topics', 'Bearer X')

    expect(client.post).toHaveBeenCalledWith('/api/lab/traces/tickets')
    const [, init] = globalThis.fetch.mock.calls[0]
    expect(init.headers['X-SpringLab-Trace']).toBe('TICKET-1')
    expect(init.headers.Authorization).toBe('Bearer X')
    // The ticket was requested before the traced request was sent.
    expect(client.post.mock.invocationCallOrder[0]).toBeLessThan(globalThis.fetch.mock.invocationCallOrder[0])
  })

  it('fetches the trace afterwards with the shared (authenticated) client using the id the server returned', async () => {
    const result = await tracedFetch('/api/topics', null)

    expect(client.get).toHaveBeenCalledWith('/api/lab/traces/trace-9')
    expect(result.trace.id).toBe('trace-9')
    expect(result.browser).toMatchObject({ status: 200, bytes: 12, contentType: 'application/json' })
  })

  it('sends no Authorization header at all for an anonymous traced request, yet still carries the ticket', async () => {
    await tracedFetch('/api/auth/me', null)

    const [, init] = globalThis.fetch.mock.calls[0]
    expect(init.headers.Authorization).toBeUndefined()
    expect(init.headers['X-SpringLab-Trace']).toBe('TICKET-1')
  })

  it('reports clearly when the server did not trace the request', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(response(200))
    await expect(tracedFetch('/api/topics', null)).rejects.toThrow(/did not return a trace id/)
  })

  it('does not trace anything when the ticket cannot be obtained (for example the session expired)', async () => {
    client.post.mockRejectedValue({ response: { status: 401 } })
    await expect(tracedFetch('/api/topics', null)).rejects.toBeTruthy()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('exposes the ticket request on its own', async () => {
    expect(await requestTraceTicket()).toBe('TICKET-1')
  })

  it('lifecycle experiments keep their behaviour: the real token, none, or one with a tampered signature', async () => {
    const target = { path: '/api/auth/me', query: null }

    await runTracedCall(target, 'AS_IS')
    await runTracedCall(target, 'NO_TOKEN')
    await runTracedCall(target, 'TAMPERED_TOKEN')

    const auth = globalThis.fetch.mock.calls.map(([, init]) => init.headers.Authorization)
    expect(auth[0]).toBe('Bearer REAL.ACCESS.TOKEN')
    expect(auth[1]).toBeUndefined()
    expect(auth[2]).toMatch(/^Bearer REAL\.ACCESS\./)
    expect(auth[2]).not.toBe(auth[0])
  })
})
