import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/client', () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import client from '../../api/client'
import { getJpaCatalog, getJpaMapping, runJpaExperiment } from './labJpaApi'

afterEach(() => vi.clearAllMocks())

describe('JPA lab API', () => {
  it('reads the catalogue and the mapping with GET', async () => {
    client.get.mockResolvedValue({ data: { ok: true } })
    await getJpaCatalog()
    await getJpaMapping()
    expect(client.get).toHaveBeenNthCalledWith(1, '/api/lab/jpa/catalog')
    expect(client.get).toHaveBeenNthCalledWith(2, '/api/lab/jpa/mapping')
  })

  it('runs an experiment by id only: a POST with no body, so nothing typed by the learner can reach the server', async () => {
    client.post.mockResolvedValue({ data: { experimentId: 'n-plus-one' } })
    const result = await runJpaExperiment('n-plus-one')
    expect(result.experimentId).toBe('n-plus-one')
    expect(client.post).toHaveBeenCalledTimes(1)
    expect(client.post.mock.calls[0]).toEqual(['/api/lab/jpa/experiments/n-plus-one/run'])
  })

  it('encodes the id so it cannot change the path', async () => {
    client.post.mockResolvedValue({ data: {} })
    await runJpaExperiment('../../admin/users')
    expect(client.post.mock.calls[0][0]).toBe('/api/lab/jpa/experiments/..%2F..%2Fadmin%2Fusers/run')
  })
})
