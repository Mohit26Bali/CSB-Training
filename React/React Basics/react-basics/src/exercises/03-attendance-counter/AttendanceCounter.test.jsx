import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AttendanceCounterApp, { MAX_ATTENDANCE } from './AttendanceCounterApp.jsx'

const count = () => screen.getByLabelText('attendance count').textContent
const press = (user, name, times = 1) =>
  Array.from({ length: times }).reduce((p) => p.then(() => user.click(screen.getByRole('button', { name }))), Promise.resolve())

describe('Attendance Counter', () => {
  it('starts at zero and counts up and down', async () => {
    const user = userEvent.setup()
    render(<AttendanceCounterApp />)
    expect(count()).toBe('0')

    await press(user, 'Increment', 3)
    expect(count()).toBe('3')
    await press(user, 'Decrement')
    expect(count()).toBe('2')
  })

  it('never goes below zero (boundary)', async () => {
    const user = userEvent.setup()
    render(<AttendanceCounterApp />)

    expect(screen.getByRole('button', { name: 'Decrement' })).toBeDisabled()
    await press(user, 'Increment', 3)
    await press(user, '-5')
    expect(count()).toBe('0')
  })

  it('Reset returns to zero', async () => {
    const user = userEvent.setup()
    render(<AttendanceCounterApp />)
    await press(user, '+5', 2)
    await press(user, 'Reset')
    expect(count()).toBe('0')
  })

  it('stops at the maximum and shows a warning (boundary)', async () => {
    const user = userEvent.setup()
    render(<AttendanceCounterApp />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await press(user, '+5', 9)
    await press(user, 'Increment', 3)
    expect(count()).toBe('48')
    await press(user, '+5')
    expect(count()).toBe(String(MAX_ATTENDANCE))
    expect(screen.getByRole('alert')).toHaveTextContent(`Maximum attendance of ${MAX_ATTENDANCE} reached.`)
    expect(screen.getByRole('button', { name: 'Increment' })).toBeDisabled()
  })
})
