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
  appName: 'Expense Manager',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M6 2h12v20l-3-2-3 2-3-2-3 2V2z', 'M9 8h6M9 12h6'],
  headline: { line1: 'Expense claims,', lead: 'made', words: ['simple', 'paperless', 'transparent', 'fast', 'fair'] },
  intro:
    'Submit business expenses, get them reviewed and see exactly where each one stands. Managers get a single queue instead of an inbox full of receipts.',
  chips: ['Travel', 'Meals', 'Accommodation', 'Office supplies', 'Software', 'Training'],
  steps: [
    { title: 'Submit', text: 'Add the date, category, amount and a short description. It takes under a minute.', icon: 'M12 4v16m8-8H4' },
    { title: 'Manager reviews', text: 'Your manager sees a clear queue and approves, or rejects with a reason.', icon: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
    { title: 'Track the result', text: 'See every expense and its status in one place. No more chasing email threads.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to submit and review expenses',
  registerSubtitle: 'Employee accounts only. Managers are set up by IT.',
  demoAccounts: [
    { label: 'Manager', username: 'manager', password: 'manager123' },
    { label: 'Employee', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { MANAGER: 'Manager', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { MANAGER: '/manager', EMPLOYEE: '/expenses' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [{ to: '/expenses', label: 'My expenses' }],
    MANAGER: [
      { to: '/manager', label: 'Dashboard', end: true },
      { to: '/manager/review', label: 'Review expenses' },
    ],
  } as Record<Role, NavItem[]>,
}
