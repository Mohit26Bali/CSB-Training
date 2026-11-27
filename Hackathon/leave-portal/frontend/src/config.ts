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
  appName: 'Leave Portal',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M8 2v4M16 2v4M3 10h18', 'M5 4h14a2 2 0 012 2v14a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2z'],
  headline: { line1: 'Time off,', lead: 'made', words: ['simple', 'transparent', 'fair', 'stress-free'] },
  intro:
    'Check your balance, request the dates you need and see the decision as soon as your manager makes it. No more leave requests lost in email.',
  chips: ['Annual leave', 'Sick leave', 'Casual leave', 'Unpaid leave'],
  steps: [
    { title: 'Check your balance', text: 'See how many days you have left for each type of leave, including days already requested.', icon: 'M3 12l5 5L21 7' },
    { title: 'Request your dates', text: 'Pick the dates and we count the working days for you. Overlaps are caught straight away.', icon: 'M12 4v16m8-8H4' },
    { title: 'Get a decision', text: 'Your manager approves or declines, and your balance updates automatically.', icon: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
  ] as Step[],
  loginSubtitle: 'Sign in to manage your leave',
  registerSubtitle: 'Employee accounts only. Managers are set up by HR.',
  demoAccounts: [
    { label: 'Manager', username: 'manager', password: 'manager123' },
    { label: 'Employee', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { MANAGER: 'Manager', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { MANAGER: '/manager/review', EMPLOYEE: '/leave' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [{ to: '/leave', label: 'My leave' }],
    MANAGER: [
      { to: '/manager/review', label: 'Review requests' },
      { to: '/manager/calendar', label: 'Team calendar' },
      { to: '/leave', label: 'My leave' },
    ],
  } as Record<Role, NavItem[]>,
}
