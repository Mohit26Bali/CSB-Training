import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'

const renderAt = (path) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
const activeLink = () => document.querySelector('.navbar__link--active')

describe('Employee Portal navigation', () => {
  it.each([
    ['/', 'Welcome to the Employee Portal', 'Home'],
    ['/employees', 'Employees', 'Employees'],
    ['/departments', 'Departments', 'Departments'],
    ['/about', 'About', 'About'],
  ])('%s shows its page and highlights its nav link', (path, heading, link) => {
    renderAt(path)
    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
    expect(activeLink()).toHaveTextContent(link)
    expect(document.querySelectorAll('.navbar__link--active')).toHaveLength(1)
  })

  it('shows the 404 page for an unknown path, inside the layout', () => {
    renderAt('/payroll/2026')
    expect(screen.getByRole('heading', { name: '404 — Page Not Found' })).toBeInTheDocument()
    expect(screen.getByText('/payroll/2026')).toBeInTheDocument()
    expect(screen.getByRole('navigation')).toBeInTheDocument()
    expect(activeLink()).toBeNull()
  })

  it('home page links lead to Employees and Departments', async () => {
    const user = userEvent.setup()
    renderAt('/')
    await user.click(screen.getByRole('link', { name: 'View employees →' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Employees' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Home' }))
    await user.click(screen.getByRole('link', { name: 'View departments →' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Departments' })).toBeInTheDocument()
    expect(activeLink()).toHaveTextContent('Departments')
  })
})
