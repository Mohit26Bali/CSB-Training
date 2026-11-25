import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EmployeeRegistrationApp from './EmployeeRegistrationApp.jsx'
import { NAME_LIMIT } from './EmployeeForm.jsx'

async function fill(user, { name = 'Asha Verma', email = 'asha@company.com', department = 'Engineering', designation = 'Engineer', salary = '95000' } = {}) {
  if (name) await user.type(screen.getByLabelText('Employee name'), name)
  if (email) await user.type(screen.getByLabelText('Email'), email)
  if (department) await user.selectOptions(screen.getByLabelText('Department'), department)
  if (designation) await user.type(screen.getByLabelText('Designation'), designation)
  if (salary) await user.type(screen.getByLabelText('Salary (₹)'), salary)
}

describe('Employee Registration form', () => {
  it('inputs are controlled: what is typed is what the field shows', async () => {
    const user = userEvent.setup()
    render(<EmployeeRegistrationApp />)
    await user.type(screen.getByLabelText('Designation'), 'QA Lead')
    expect(screen.getByLabelText('Designation')).toHaveValue('QA Lead')
  })

  it('submits without reloading, shows the data below the form and clears the form', async () => {
    const user = userEvent.setup()
    render(<EmployeeRegistrationApp />)
    await fill(user)
    await user.click(screen.getByRole('button', { name: 'Register' }))

    const row = screen.getAllByRole('row')[1]
    expect(within(row).getByText('Asha Verma')).toBeInTheDocument()
    expect(within(row).getByText('₹95,000')).toBeInTheDocument()
    expect(screen.getByLabelText('Employee name')).toHaveValue('')
    expect(screen.getByLabelText('Department')).toHaveValue('')
    expect(screen.getByRole('heading', { name: 'Submitted employees (1)' })).toBeInTheDocument()
  })

  it('does not submit with missing fields or a non-positive salary (invalid input)', async () => {
    const user = userEvent.setup()
    render(<EmployeeRegistrationApp />)
    await user.click(screen.getByRole('button', { name: 'Register' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Please fill in: name, email, department, designation, salary')

    await fill(user, { salary: '0' })
    await user.click(screen.getByRole('button', { name: 'Register' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Salary must be a positive number')
    expect(screen.getByText('Nothing submitted yet.')).toBeInTheDocument()
    expect(screen.getByLabelText('Employee name')).toHaveValue('Asha Verma')        // kept, not cleared
  })

  it('counts the remaining name characters and stops at the limit (boundary)', async () => {
    const user = userEvent.setup()
    render(<EmployeeRegistrationApp />)
    expect(screen.getByText(`${NAME_LIMIT} characters remaining`)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Employee name'), 'Asha')
    expect(screen.getByText(`${NAME_LIMIT - 4} characters remaining`)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Employee name'), 'x'.repeat(60))
    expect(screen.getByLabelText('Employee name').value).toHaveLength(NAME_LIMIT)
    expect(screen.getByText('0 characters remaining')).toBeInTheDocument()
  })
})
