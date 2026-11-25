import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'

const renderAt = (path) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
const heading = () => screen.getByRole('heading', { level: 1 }).textContent

async function logIn(user, username = 'admin', password = 'admin123') {
  if (username) await user.type(screen.getByLabelText('Username'), username)
  if (password) await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Protected admin route', () => {
  it('the home page is public', () => {
    renderAt('/')
    expect(heading()).toBe('Welcome')
  })

  it.each(['/admin', '/admin/reports'])('%s redirects to Login when not logged in', (path) => {
    renderAt(path)
    expect(heading()).toBe('Login')
    expect(screen.getByRole('status')).toHaveTextContent(`Please log in to open ${path}.`)
  })

  it('logging in from /login goes to the Admin Dashboard', async () => {
    const user = userEvent.setup()
    renderAt('/login')
    await logIn(user)
    expect(heading()).toBe('Admin Dashboard')
    expect(screen.getByText('Signed in as admin')).toBeInTheDocument()
  })

  it('returns the user to the originally requested route after login (challenge)', async () => {
    const user = userEvent.setup()
    renderAt('/admin/reports')
    await logIn(user)
    expect(heading()).toBe('Admin Reports')
  })

  it('rejects wrong or missing credentials and stays on Login', async () => {
    const user = userEvent.setup()
    renderAt('/admin')
    await logIn(user, 'admin', 'wrong')
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(heading()).toBe('Login')

    await user.clear(screen.getByLabelText('Username'))
    await user.clear(screen.getByLabelText('Password'))
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter your username and password.')
  })

  it('Logout returns to Login and protects the admin pages again', async () => {
    const user = userEvent.setup()
    renderAt('/login')
    await logIn(user)
    await user.click(screen.getByRole('button', { name: 'Logout' }))
    expect(heading()).toBe('Login')

    await user.click(screen.getByRole('link', { name: 'Dashboard' }))
    expect(heading()).toBe('Login')
  })
})
