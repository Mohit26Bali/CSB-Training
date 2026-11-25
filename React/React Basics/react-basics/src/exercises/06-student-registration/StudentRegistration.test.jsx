import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StudentRegistrationApp from './StudentRegistrationApp.jsx'
import { validate } from './validate.js'

const valid = { name: 'Priya Menon', email: 'priya@example.com', age: '21', course: 'Data Science', mobile: '9876543210' }

async function fill(user, data) {
  if (data.name) await user.type(screen.getByLabelText('Name'), data.name)
  if (data.email) await user.type(screen.getByLabelText('Email'), data.email)
  if (data.age) await user.type(screen.getByLabelText('Age'), data.age)
  if (data.course) await user.selectOptions(screen.getByLabelText('Course'), data.course)
  if (data.mobile) await user.type(screen.getByLabelText('Mobile Number'), data.mobile)
}

describe('validate()', () => {
  it('accepts a valid form', () => {
    expect(validate(valid)).toEqual({})
  })

  it('requires every field', () => {
    expect(Object.keys(validate({ name: ' ', email: '', age: '', course: '', mobile: '' })))
      .toEqual(['name', 'email', 'age', 'course', 'mobile'])
  })

  it('accepts ages 18 and 60 but not 17, 61 or 20.5 (boundaries)', () => {
    expect(validate({ ...valid, age: '18' }).age).toBeUndefined()
    expect(validate({ ...valid, age: '60' }).age).toBeUndefined()
    expect(validate({ ...valid, age: '17' }).age).toBe('Age must be a whole number between 18 and 60')
    expect(validate({ ...valid, age: '61' }).age).toBeDefined()
    expect(validate({ ...valid, age: '20.5' }).age).toBeDefined()
  })

  it('checks the email format and exactly 10 mobile digits (invalid input)', () => {
    expect(validate({ ...valid, email: 'priya@' }).email).toBe('Enter a valid email, e.g. name@example.com')
    expect(validate({ ...valid, email: 'priya example.com' }).email).toBeDefined()
    expect(validate({ ...valid, mobile: '987654321' }).mobile).toBe('Mobile number must be exactly 10 digits')
    expect(validate({ ...valid, mobile: '98765432ab' }).mobile).toBeDefined()
  })
})

describe('Student Registration form', () => {
  it('keeps Submit disabled while invalid and shows a field message after leaving it', async () => {
    const user = userEvent.setup()
    render(<StudentRegistrationApp />)
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()

    await user.type(screen.getByLabelText('Mobile Number'), '12345')
    await user.tab()
    expect(screen.getByText('Mobile number must be exactly 10 digits')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows the submitted data only after a valid submit, and Reset clears everything', async () => {
    const user = userEvent.setup()
    render(<StudentRegistrationApp />)
    await fill(user, valid)

    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(screen.getByRole('status')).toHaveTextContent('Registration successful!')
    expect(screen.getByRole('status')).toHaveTextContent('priya@example.com')

    await user.click(screen.getByRole('button', { name: 'Reset' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
  })
})
