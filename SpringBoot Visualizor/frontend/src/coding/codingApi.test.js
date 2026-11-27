import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

import client from '../api/client'
import {
  getCodingProblem,
  getCodingProgress,
  getCodingTopics,
  getSubmission,
  JUDGING_TIMEOUT_MS,
  listCodingProblems,
  listSubmissions,
  runCode,
  submitCode,
} from './codingApi'

beforeEach(() => {
  vi.clearAllMocks()
  client.get.mockResolvedValue({ data: { ok: true } })
  client.post.mockResolvedValue({ data: { verdict: 'ACCEPTED' } })
})

describe('coding API calls', () => {
  it('reads the catalogue, a problem, history, a submission and progress from the documented endpoints', async () => {
    await getCodingTopics()
    await listCodingProblems()
    await getCodingProblem('retry-with-bounded-backoff')
    await listSubmissions('retry-with-bounded-backoff')
    await getSubmission(42)
    await getCodingProgress()
    expect(client.get.mock.calls.map((c) => c[0])).toEqual([
      '/api/coding/topics',
      '/api/coding/problems',
      '/api/coding/problems/retry-with-bounded-backoff',
      '/api/coding/problems/retry-with-bounded-backoff/submissions',
      '/api/coding/submissions/42',
      '/api/coding/progress/me',
    ])
  })

  it('Run posts to /run and Submit posts to /submit, each with only the source code', async () => {
    const signal = new AbortController().signal
    await runCode('p', 'class A {}', signal)
    await submitCode('p', 'class B {}', signal)
    const [run, submit] = client.post.mock.calls
    expect(run[0]).toBe('/api/coding/problems/p/run')
    expect(run[1]).toEqual({ sourceCode: 'class A {}' })
    expect(submit[0]).toBe('/api/coding/problems/p/submit')
    expect(submit[1]).toEqual({ sourceCode: 'class B {}' })
    for (const call of [run, submit]) {
      expect(Object.keys(call[1])).toEqual(['sourceCode']) // no tests, problem key, mode or expected result is ever sent
      expect(call[2].signal).toBe(signal)
      expect(call[2].timeout).toBe(JUDGING_TIMEOUT_MS)
    }
  })

  it('waits longer than the server hard timeout (60 s) instead of giving up early', () => {
    expect(JUDGING_TIMEOUT_MS).toBeGreaterThan(60_000)
  })

  it('encodes a slug and an id so they cannot change the path', async () => {
    await getCodingProblem('a/b?c')
    await getSubmission('1/../2')
    expect(client.get.mock.calls[0][0]).toBe('/api/coding/problems/a%2Fb%3Fc')
    expect(client.get.mock.calls[1][0]).toBe('/api/coding/submissions/1%2F..%2F2')
  })

  it('returns the response body unchanged', async () => {
    client.post.mockResolvedValue({ data: { verdict: 'WRONG_ANSWER', passed: 1, total: 3 } })
    expect(await runCode('p', 'x')).toEqual({ verdict: 'WRONG_ANSWER', passed: 1, total: 3 })
  })
})
