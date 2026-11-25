import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EmployeeSearchApp from './EmployeeSearchApp.jsx'
import { employees } from './employees.js'
import { filterEmployees } from './filterEmployees.js'

const rows = () => screen.getAllByRole('row').length - 1     // minus the header row

describe('Employee Search', () => {
  it('has at least eight employees and shows them all at first', () => {
    render(<EmployeeSearchApp />)
    expect(employees.length).toBeGreaterThanOrEqual(8)
    expect(rows()).toBe(employees.length)
  })

  it('filters by name, ignoring case', async () => {
    const user = userEvent.setup()
    render(<EmployeeSearchApp />)

    await user.type(screen.getByRole('searchbox'), 'ASHA')
    expect(rows()).toBe(1)
    expect(screen.getByText('Asha Verma')).toBeInTheDocument()
  })

  it('also matches the employee ID', () => {
    expect(filterEmployees(employees, 'emp-104', 'All').map((e) => e.name)).toEqual(['Karan Shah'])
  })

  it('shows "No employees found" when nothing matches (invalid input)', async () => {
    const user = userEvent.setup()
    render(<EmployeeSearchApp />)

    await user.type(screen.getByRole('searchbox'), 'zzz')
    expect(screen.getByText('No employees found')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByText(`0 of ${employees.length} employees`)).toBeInTheDocument()
  })

  it('combines the department filter with the search and counts the matches', async () => {
    const user = userEvent.setup()
    render(<EmployeeSearchApp />)

    await user.selectOptions(screen.getByRole('combobox'), 'Engineering')
    expect(screen.getByText(`3 of ${employees.length} employees`)).toBeInTheDocument()
    await user.type(screen.getByRole('searchbox'), 'ravi')
    expect(rows()).toBe(1)
  })

  it('treats an empty or blank search as "show everything" (boundary)', () => {
    expect(filterEmployees(employees, '   ', 'All')).toHaveLength(employees.length)
  })
})
