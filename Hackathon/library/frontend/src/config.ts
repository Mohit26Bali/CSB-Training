import type { Role } from './api/types'

/**
 * Everything that makes this app different from the other hackathon apps lives here:
 * branding, the login-page copy, demo accounts, and where each role lands and what it can navigate to.
 */

export interface NavItem {
  to: string
  label: string
  end?: boolean
}

export interface Step {
  title: string
  text: string
  /** an SVG path "d" attribute, drawn at 24x24 */
  icon: string
}

export interface DemoAccount {
  label: string
  username: string
  password: string
}

export const config = {
  appName: 'Library Desk',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 5a2 2 0 012-2h12v16H6a2 2 0 00-2 2V5z', 'M18 19v2H6', 'M9 7h6'],
  headline: { line1: 'Your next', lead: 'favourite', words: ['story', 'textbook', 'biography', 'adventure'] },
  intro:
    'Search the catalogue, reserve a copy in a click and collect it from the desk. See your due dates at a glance, so a late fine never surprises you.',
  chips: ['Fiction', 'Science', 'Technology', 'History', 'Biography', 'Self-help'],
  steps: [
    { title: 'Search', text: 'By title, author, ISBN or category. Filter to books you can borrow right now.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Reserve', text: 'We hold a copy for you until the librarian hands it over.', icon: 'M12 4v16m8-8H4' },
    { title: 'Read and return', text: 'Fourteen days to read. Return on time and the copy goes back on the shelf for the next reader.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to reserve books and track your loans',
  registerSubtitle: 'Member accounts only. Librarians are set up by the library office.',
  demoAccounts: [
    { label: 'Librarian', username: 'librarian', password: 'librarian123' },
    { label: 'Member (overdue book, one reservation)', username: 'aarav.sharma', password: 'member123' },
    { label: 'Member', username: 'diya.patel', password: 'member123' },
  ] as DemoAccount[],
  roleLabels: { LIBRARIAN: 'Librarian', MEMBER: 'Member' } as Record<Role, string>,
  homePath: { LIBRARIAN: '/desk', MEMBER: '/catalogue' } as Record<Role, string>,
  nav: {
    MEMBER: [
      { to: '/catalogue', label: 'Catalogue' },
      { to: '/shelf', label: 'My shelf' },
    ],
    LIBRARIAN: [
      { to: '/desk', label: 'Desk', end: true },
      { to: '/desk/loans', label: 'Reservations and loans' },
      { to: '/desk/books', label: 'Books' },
    ],
  } as Record<Role, NavItem[]>,
}
