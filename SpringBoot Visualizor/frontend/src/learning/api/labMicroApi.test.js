import { beforeEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import { createMicroRun, executeMicroRun, getMicroExperiments, getMicroMeta, getMicroRun, getMicroTopics, resetMicroRun } from './labMicroApi'

vi.mock('../../api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

describe('microservices lab api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('reads meta, topics and experiments with GET', async () => {
    client.get.mockResolvedValue({ data: [1] })
    await getMicroMeta()
    await getMicroTopics()
    await getMicroExperiments()
    expect(client.get.mock.calls.map((c) => c[0])).toEqual(['/api/lab/microservices/meta', '/api/lab/microservices/topics', '/api/lab/microservices/experiments'])
  })

  it('creates a run from an experiment id and What-If values only', async () => {
    client.post.mockResolvedValue({ data: { runId: 'a'.repeat(32) } })
    await createMicroRun('res-bulkhead', { permits: 'shared' })
    expect(client.post).toHaveBeenCalledWith('/api/lab/microservices/runs', { experimentId: 'res-bulkhead', whatIf: { permits: 'shared' } })
    expect(Object.keys(client.post.mock.calls[0][1]).sort()).toEqual(['experimentId', 'whatIf'])
  })

  it('executes, reads and resets a run by its id, encoded so it cannot escape the path', async () => {
    client.post.mockResolvedValue({ data: {} })
    client.get.mockResolvedValue({ data: {} })
    await executeMicroRun('abc')
    await resetMicroRun('abc')
    await getMicroRun('../x?y=1')
    expect(client.post.mock.calls.map((c) => c[0])).toEqual(['/api/lab/microservices/runs/abc/execute', '/api/lab/microservices/runs/abc/reset'])
    expect(client.get.mock.calls[0][0]).toBe('/api/lab/microservices/runs/..%2Fx%3Fy%3D1')
    expect(client.post.mock.calls[0]).toHaveLength(1)
  })
})
