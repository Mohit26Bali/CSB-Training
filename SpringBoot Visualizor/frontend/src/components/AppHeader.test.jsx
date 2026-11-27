import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

let currentUser = { id: 1, username: 'sam', role: 'STUDENT' }
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: currentUser, logout: vi.fn() }) }))

import AppHeader from './AppHeader'

afterEach(cleanup)

function renderAt(path, user) {
  currentUser = user
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppHeader />
    </MemoryRouter>,
  )
}

const main = () => screen.getAllByRole('navigation', { name: 'Main' })[0]

describe('AppHeader', () => {
  it('marks the current page as active with aria-current', () => {
    renderAt('/arena', { id: 1, username: 'sam', role: 'STUDENT' })
    expect(within(main()).getByRole('link', { name: 'Challenge Arena' }).getAttribute('aria-current')).toBe('page')
    expect(within(main()).getByRole('link', { name: 'Dashboard' }).getAttribute('aria-current')).toBeNull()
  })

  it('keeps the bar short: the three lessons live in one Learn menu that is closed until opened', () => {
    renderAt('/', { id: 1, username: 'sam', role: 'STUDENT' })
    const learn = within(main()).getByRole('button', { name: /Learn/ })
    expect(learn.getAttribute('aria-expanded')).toBe('false')
    expect(within(main()).queryByRole('link', { name: /JWT security/ })).toBeNull()
    fireEvent.click(learn)
    expect(learn.getAttribute('aria-expanded')).toBe('true')
    const links = within(main()).getAllByRole('link').map((l) => l.getAttribute('href'))
    expect(links).toEqual(expect.arrayContaining(['/learn/rest-lifecycle', '/learn/jwt-security', '/learn/jpa-relationships', '/learn/jdbc', '/learn/microservices', '/learn/coding']))
  })

  it('the Learn menu closes on Escape and is the active item while a lesson is open', () => {
    renderAt('/learn/jwt-security', { id: 1, username: 'sam', role: 'STUDENT' })
    const learn = within(main()).getByRole('button', { name: /Learn/ })
    expect(learn.className).toContain('border-accent')
    fireEvent.click(learn)
    expect(learn.getAttribute('aria-expanded')).toBe('true')
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(learn.getAttribute('aria-expanded')).toBe('false')
  })

  it('shows management links only to staff (the backend enforces them regardless)', () => {
    renderAt('/', { id: 1, username: 'sam', role: 'STUDENT' })
    expect(screen.queryByRole('link', { name: 'Questions' })).toBeNull()
    expect(screen.queryByRole('button', { name: /Manage/ })).toBeNull()
    cleanup()

    renderAt('/', { id: 2, username: 'tia', role: 'TEACHER' })
    expect(within(main()).getByRole('link', { name: 'Questions' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Manage/ })).toBeNull()
    cleanup()

    renderAt('/', { id: 3, username: 'root', role: 'ADMIN' })
    fireEvent.click(within(main()).getByRole('button', { name: /Manage/ }))
    expect(within(main()).getByRole('link', { name: /Questions/ })).toBeTruthy()
    expect(within(main()).getByRole('link', { name: /Users/ })).toBeTruthy()
  })

  it('the account menu shows the signed-in user and offers Log out', () => {
    renderAt('/', { id: 1, username: 'sam', role: 'STUDENT' })
    fireEvent.click(screen.getByRole('button', { name: 'Account menu for sam' }))
    expect(screen.getByText('Signed in as')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeTruthy()
  })

  it('the mobile menu toggles with the keyboard-accessible button, groups its links and reports its state', () => {
    renderAt('/', { id: 3, username: 'root', role: 'ADMIN' })
    const toggle = screen.getByRole('button', { name: 'Open menu' })
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: 'Close menu' }).getAttribute('aria-expanded')).toBe('true')
    const mobile = screen.getByRole('navigation', { name: 'Main (mobile)' })
    expect(within(mobile).getByText('Learn')).toBeTruthy()
    expect(within(mobile).getByText('Manage')).toBeTruthy()
    expect(within(mobile).getAllByRole('link').length).toBeGreaterThanOrEqual(8)
    fireEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(screen.queryByRole('navigation', { name: 'Main (mobile)' })).toBeNull()
  })
})
