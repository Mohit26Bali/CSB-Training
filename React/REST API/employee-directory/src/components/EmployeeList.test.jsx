import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EmployeeList from './EmployeeList.jsx'

const users = [
  {
    id: 1,
    firstName: 'Emily',
    lastName: 'Johnson',
    email: 'emily@x.dummyjson.com',
    phone: '+81 965-431-3024',
    company: { department: 'Engineering', name: 'Dooley LLC', title: 'Sales Manager' },
  },
  {
    id: 2,
    firstName: 'Michael',
    lastName: 'Williams',
    email: 'michael@x.dummyjson.com',
    phone: '+49 258-627-6644',
    company: { department: 'Support', name: 'Spinka LLC', title: 'Support Specialist' },
  },
]

const ok = (body) => Promise.resolve({ ok: true, json: () => Promise.resolve(body) })

afterEach(() => vi.unstubAllGlobals())

test('calls the API and shows Loading... first', async () => {
  const fetchMock = vi.fn(() => ok({ users }))
  vi.stubGlobal('fetch', fetchMock)
  render(<EmployeeList />)
  expect(screen.getByText('Loading...')).toBeInTheDocument()
  expect(await screen.findByText('Emily Johnson')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('https://dummyjson.com/users')
  expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
})

test('displays the selected fields for each employee', async () => {
  vi.stubGlobal('fetch', vi.fn(() => ok({ users })))
  render(<EmployeeList />)
  expect(await screen.findByText('Michael Williams')).toBeInTheDocument()
  expect(screen.getByText('emily@x.dummyjson.com')).toBeInTheDocument()
  expect(screen.getByText('+49 258-627-6644')).toBeInTheDocument()
  expect(screen.getByText('Engineering')).toBeInTheDocument()
  expect(screen.getByText('Spinka LLC')).toBeInTheDocument()
})

test('shows an error message when the request fails', async () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Failed to fetch'))))
  render(<EmployeeList />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load employees. Failed to fetch')
})

test('shows an error message for a non-OK HTTP status', async () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 500, statusText: 'Internal Server Error' })))
  render(<EmployeeList />)
  expect(await screen.findByRole('alert')).toHaveTextContent('500 Internal Server Error')
})

test('shows No employees found for an empty collection', async () => {
  vi.stubGlobal('fetch', vi.fn(() => ok({ users: [] })))
  render(<EmployeeList />)
  expect(await screen.findByText('No employees found')).toBeInTheDocument()
})

test('search filters by first or last name', async () => {
  vi.stubGlobal('fetch', vi.fn(() => ok({ users })))
  render(<EmployeeList />)
  await screen.findByText('Emily Johnson')
  const box = screen.getByRole('searchbox')
  await userEvent.type(box, 'william')
  expect(screen.queryByText('Emily Johnson')).not.toBeInTheDocument()
  expect(screen.getByText('Michael Williams')).toBeInTheDocument()
  await userEvent.clear(box)
  await userEvent.type(box, 'zzz')
  expect(screen.getByText('No employees found')).toBeInTheDocument()
})

test('Reload calls the API again', async () => {
  const fetchMock = vi.fn(() => ok({ users }))
  vi.stubGlobal('fetch', fetchMock)
  render(<EmployeeList />)
  await screen.findByText('Emily Johnson')
  const calls = fetchMock.mock.calls.length
  await userEvent.click(screen.getByRole('button', { name: 'Reload' }))
  await screen.findByText('Emily Johnson')
  expect(fetchMock.mock.calls.length).toBe(calls + 1)
})
