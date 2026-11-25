import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ValidatedFormApp from './ValidatedFormApp.jsx'
import { validateEmployee } from './validateEmployee.js'

const valid = { name: 'Asha Verma', email: 'asha@company.com', mobile: '9876543210', password: 'Secret123', confirmPassword: 'Secret123' }

describe('validateEmployee()', () => {
  it('accepts a valid form', () => {
    expect(validateEmployee(valid)).toEqual({})
  })

  it('reports every required field', () => {
    expect(Object.keys(validateEmployee({ name: '', email: '', mobile: '', password: '', confirmPassword: '' })))
      .toEqual(['name', 'email', 'mobile', 'password', 'confirmPassword'])
  })

  it('checks email format and mobile length/format (invalid input)', () => {
    expect(validateEmployee({ ...valid, email: 'asha@company' }).email).toBe('Enter a valid email, e.g. name@example.com')
    expect(validateEmployee({ ...valid, mobile: '987654321' }).mobile).toMatch(/10 digits/)
    expect(validateEmployee({ ...valid, mobile: '98765432100' }).mobile).toBeDefined()
    expect(validateEmployee({ ...valid, mobile: '1234567890' }).mobile).toBeDefined()      // must start 6-9
    expect(validateEmployee({ ...valid, mobile: '98765abcde' }).mobile).toBeDefined()
  })

  it('needs at least 8 password characters (boundary) and matching passwords', () => {
    expect(validateEmployee({ ...valid, password: 'Seven77', confirmPassword: 'Seven77' }).password)
      .toBe('Password must be at least 8 characters')
    expect(validateEmployee({ ...valid, password: 'Eight888', confirmPassword: 'Eight888' }).password).toBeUndefined()
    expect(validateEmployee({ ...valid, confirmPassword: 'Different1' }).confirmPassword).toBe('Passwords do not match')
  })
})

describe('Validated form', () => {
  it('shows a message next to a field after leaving it, and keeps Submit disabled', async () => {
    const user = userEvent.setup()
    render(<ValidatedFormApp />)
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()

    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.tab()
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter a valid email, e.g. name@example.com')
    expect(screen.queryByText('Name is required')).not.toBeInTheDocument()             // not touched yet
  })

  it('allows submission only when every check passes', async () => {
    const user = userEvent.setup()
    render(<ValidatedFormApp />)
    await user.type(screen.getByLabelText('Name'), valid.name)
    await user.type(screen.getByLabelText('Email'), valid.email)
    await user.type(screen.getByLabelText('Mobile number'), valid.mobile)
    await user.type(screen.getByLabelText('Password (min 8 characters)'), valid.password)
    await user.type(screen.getByLabelText('Confirm password'), 'Secret12')
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()               // passwords differ

    await user.type(screen.getByLabelText('Confirm password'), '3')
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(screen.getByRole('status')).toHaveTextContent('Registered Asha Verma (asha@company.com, 9876543210).')
    expect(screen.getByLabelText('Name')).toHaveValue('')
  })
})
