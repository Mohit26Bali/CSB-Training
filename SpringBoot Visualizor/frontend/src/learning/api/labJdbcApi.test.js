import { beforeEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import { getJdbcCatalog, getJdbcMapping, runJdbcExperiment } from './labJdbcApi'

vi.mock('../../api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

describe('JDBC lab api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('reads the catalogue and the mapping with GET', async () => {
    client.get.mockResolvedValue({ data: { ok: 1 } })
    expect(await getJdbcCatalog()).toEqual({ ok: 1 })
    expect(client.get).toHaveBeenCalledWith('/api/lab/jdbc/catalog')
    await getJdbcMapping()
    expect(client.get).toHaveBeenCalledWith('/api/lab/jdbc/mapping')
  })

  it('runs an experiment by id only: no request body', async () => {
    client.post.mockResolvedValue({ data: { experimentId: 'x' } })
    await runJdbcExperiment('jdbc-commit')
    expect(client.post).toHaveBeenCalledTimes(1)
    expect(client.post.mock.calls[0]).toEqual(['/api/lab/jdbc/experiments/jdbc-commit/run'])
  })

  it('encodes the id so it cannot escape the path', async () => {
    client.post.mockResolvedValue({ data: {} })
    await runJdbcExperiment('../admin?x=1')
    expect(client.post.mock.calls[0][0]).toBe('/api/lab/jdbc/experiments/..%2Fadmin%3Fx%3D1/run')
  })
})
