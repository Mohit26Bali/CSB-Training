import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'
import { initialEmployees } from './employees.js'

const renderAt = (path) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)

describe('Employee details with URL parameters', () => {
  it('lists at least five employees and "/" redirects to the list', () => {
    renderAt('/')
    expect(screen.getByRole('heading', { name: 'Employees' })).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(initialEmployees.length)
    expect(initialEmployees.length).toBeGreaterThanOrEqual(5)
  })

  it('clicking an employee opens /employees/:employeeId with that employee', async () => {
    const user = userEvent.setup()
    renderAt('/employees')
    await user.click(screen.getByRole('link', { name: /Hana Kapoor/ }))

    expect(screen.getByRole('heading', { name: 'Hana Kapoor' })).toBeInTheDocument()
    expect(screen.getByText('hana.kapoor@company.com')).toBeInTheDocument()
  })

  it('reads the id straight from the URL', () => {
    renderAt('/employees/104')
    expect(screen.getByRole('heading', { name: 'Karan Shah' })).toBeInTheDocument()
  })

  it.each(['999', 'abc', '101abc', '-1'])('handles the invalid id "%s" gracefully', (id) => {
    renderAt(`/employees/${id}`)
    expect(screen.getByRole('heading', { name: 'Employee not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the employee list' })).toBeInTheDocument()
  })

  it('Edit opens /employees/:employeeId/edit, and Save updates the details', async () => {
    const user = userEvent.setup()
    renderAt('/employees/101')
    await user.click(screen.getByRole('link', { name: 'Edit' }))
    expect(screen.getByRole('heading', { name: 'Edit Asha Verma' })).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Role'))
    await user.type(screen.getByLabelText('Role'), 'Senior Software Engineer')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByRole('heading', { name: 'Asha Verma' })).toBeInTheDocument()
    expect(screen.getByText('Senior Software Engineer')).toBeInTheDocument()
  })

  it('cannot save an empty field', async () => {
    const user = userEvent.setup()
    renderAt('/employees/102/edit')
    await user.clear(screen.getByLabelText('Name'))
    expect(screen.getByText('All fields are required.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
  })

  it('editing an unknown id shows "Employee not found"', () => {
    renderAt('/employees/777/edit')
    expect(screen.getByRole('heading', { name: 'Employee not found' })).toBeInTheDocument()
  })

  it('unknown paths show the 404 page', () => {
    renderAt('/payroll')
    expect(screen.getByRole('heading', { name: '404 — Page Not Found' })).toBeInTheDocument()
  })
})
