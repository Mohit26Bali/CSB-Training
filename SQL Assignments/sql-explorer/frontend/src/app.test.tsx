import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App } from './App'
import { api, ApiError } from './api'

const summary = (id: string, category: 'DDL' | 'DML' | 'SELECT' | 'Capstone', title: string) => ({
  id, title, category, db: `sx_${id}_db`, originalDb: `${id}_db`, tables: 3, rows: 42, queries: 8, negatives: 6, dml: 0,
})

afterEach(() => vi.restoreAllMocks())

describe('api client', () => {
  it('turns an error answer into an ApiError with the server message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ error: 'Run one statement at a time.' }) }))
    await expect(api.runSql('01', 'SELECT 1; SELECT 2')).rejects.toMatchObject({ status: 400, message: 'Run one statement at a time.' })
  })

  it('explains when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(api.assignments()).rejects.toThrow('Cannot reach the server')
  })

  it('encodes path parts so a name cannot change the URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({}) })
    vi.stubGlobal('fetch', fetchMock)
    await api.table('01', 'a/../b?x=1', 2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/assignments/01/tables/a%2F..%2Fb%3Fx%3D1?page=2&pageSize=15')
  })
})

describe('home page', () => {
  it('lists every assignment with its numbers, grouped in the sidebar', async () => {
    vi.spyOn(api, 'assignments').mockResolvedValue([summary('01', 'DDL', 'Organization Database (DDL)'), summary('05', 'DML', 'Banking Transactions (DML)')])
    render(<MemoryRouter><App /></MemoryRouter>)
    const cards = await screen.findAllByRole('link', { name: /Organization Database/ })
    expect(cards.length).toBeGreaterThanOrEqual(2) // sidebar link and card
    expect(screen.getByRole('navigation', { name: 'Assignments' })).toHaveTextContent('DML: changing data')
    expect(screen.getAllByText('42').length).toBeGreaterThan(0)
  })

  it('shows a readable error and a retry when the server is down', async () => {
    const spy = vi.spyOn(api, 'assignments').mockRejectedValue(new ApiError(0, 'Cannot reach the server. Is the backend running?'))
    render(<MemoryRouter><App /></MemoryRouter>)
    const alerts = await screen.findAllByRole('alert')
    expect(alerts[0]).toHaveTextContent('Cannot reach the server')
    spy.mockResolvedValue([summary('01', 'DDL', 'Organization Database (DDL)')])
    await userEvent.click(screen.getAllByRole('button', { name: 'Try again' })[0])
    await waitFor(() => expect(screen.getAllByRole('link', { name: /Organization Database/ }).length).toBeGreaterThan(0))
  })

  it('has a way past the navigation for keyboard users', async () => {
    vi.spyOn(api, 'assignments').mockResolvedValue([])
    render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
  })
})
