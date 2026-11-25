import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'
import { SAMPLE_EMPLOYEES } from './context/EmployeeContext.jsx'
import { validateEmployee } from './validateEmployee.js'

const renderAt = (path, initialEmployees) =>
  render(<MemoryRouter initialEntries={[path]}><App initialEmployees={initialEmployees} /></MemoryRouter>)
const heading = () => screen.getByRole('heading', { level: 1 }).textContent

async function fillForm(user, values) {
  for (const [label, value] of Object.entries(values)) {
    const field = screen.getByLabelText(label)
    if (field.tagName === 'SELECT') await user.selectOptions(field, value)
    else {
      await user.clear(field)
      await user.type(field, value)
    }
  }
}

describe('validateEmployee()', () => {
  const ok = { name: 'Neha Joshi', email: 'neha@company.com', department: 'Quality', designation: 'QA', salary: '60000' }

  it('accepts a valid employee', () => {
    expect(validateEmployee(ok, SAMPLE_EMPLOYEES)).toEqual({})
  })

  it('rejects empty, invalid, duplicate and out-of-range values', () => {
    expect(Object.keys(validateEmployee({ name: '', email: '', department: '', designation: '', salary: '' }, [])))
      .toEqual(['name', 'email', 'department', 'designation', 'salary'])
    expect(validateEmployee({ ...ok, email: 'ASHA.VERMA@company.com' }, SAMPLE_EMPLOYEES).email)
      .toBe('Another employee already uses this email')
    expect(validateEmployee({ ...ok, salary: '0' }, []).salary).toBe('Salary must be greater than 0')
    expect(validateEmployee({ ...ok, name: 'A' }, []).name).toBe('Name must be at least 2 characters')
  })
})

describe('Employee Management SPA', () => {
  it('/ redirects to the list, which comes from shared state', () => {
    renderAt('/')
    expect(heading()).toBe(`Employees (${SAMPLE_EMPLOYEES.length})`)
  })

  it('create → navigates to the new employee, who is then in the list', async () => {
    const user = userEvent.setup()
    renderAt('/employees/new')
    await fillForm(user, { Name: 'Neha Joshi', Email: 'neha@company.com', Department: 'Quality', Designation: 'QA Engineer', 'Salary (₹)': '60000' })
    await user.click(screen.getByRole('button', { name: 'Add employee' }))

    expect(heading()).toBe('Neha Joshi')
    expect(screen.getByRole('status')).toHaveTextContent('Neha Joshi was added.')
    await user.click(screen.getByRole('link', { name: 'Back to list' }))
    expect(heading()).toBe('Employees (5)')
    expect(screen.getByRole('link', { name: 'Neha Joshi' })).toBeInTheDocument()
  })

  it('the form shows errors and does not submit invalid data', async () => {
    const user = userEvent.setup()
    renderAt('/employees/new')
    await user.click(screen.getByRole('button', { name: 'Add employee' }))
    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(heading()).toBe('Add employee')
  })

  it('edit → saves and navigates back to the details', async () => {
    const user = userEvent.setup()
    renderAt('/employees/2/edit')
    expect(screen.getByLabelText('Name')).toHaveValue('Ravi Iyer')                 // pre-filled
    await fillForm(user, { Designation: 'Senior Backend Developer' })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(heading()).toBe('Ravi Iyer')
    expect(screen.getByText('Senior Backend Developer')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Changes saved.')
  })

  it('delete → asks to confirm, removes the employee and returns to the list', async () => {
    const user = userEvent.setup()
    renderAt('/employees/4')
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(heading()).toBe('Employees (3)')
    expect(screen.getByRole('status')).toHaveTextContent('Karan Shah was deleted.')
    expect(screen.queryByRole('link', { name: 'Karan Shah' })).not.toBeInTheDocument()
  })

  it.each(['/employees/99', '/employees/abc', '/employees/99/edit'])('%s shows "Employee not found"', (path) => {
    renderAt(path)
    expect(heading()).toBe('Employee not found')
  })

  it('an empty list is handled', () => {
    renderAt('/employees', [])
    expect(screen.getByText(/No employees yet/)).toBeInTheDocument()
  })

  it('a search with no matches is handled', async () => {
    const user = userEvent.setup()
    renderAt('/employees')
    await user.type(screen.getByLabelText('Search employees'), 'zzz')
    expect(screen.getByText('No employees match "zzz".')).toBeInTheDocument()
  })

  it('unknown paths show the 404 page', () => {
    renderAt('/payroll')
    expect(heading()).toBe('404 — Page Not Found')
  })
})
