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
  appName: 'Visitor Pass',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 5h16v14H4z', 'M9 11a2 2 0 104 0 2 2 0 00-4 0', 'M7 17c.6-1.7 2-2.5 4-2.5s3.4.8 4 2.5'],
  headline: { line1: 'Expecting', lead: 'a', words: ['client', 'candidate', 'vendor', 'guest'] },
  intro:
    'Invite your visitor in a minute and they get a pass code for the gate. Security sees everyone expected today, checks them in and out and you see when they arrive.',
  chips: ['Clients', 'Interviews', 'Vendors', 'Deliveries', 'Auditors'],
  steps: [
    { title: 'Send an invitation', text: 'Visitor name, phone, purpose and the day they are coming.', icon: 'M12 4v16m8-8H4' },
    { title: 'They show their pass', text: 'Security finds them by name, phone or pass code and checks them in.', icon: 'M4 5h16v14H4zM8 9h8M8 13h5' },
    { title: 'Know when they arrive', text: 'Your invitation shows when they came in and when they left.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to invite visitors or run the gate',
  registerSubtitle: 'Employee accounts only. Security officers are set up by the facilities team.',
  demoAccounts: [
    { label: 'Security officer', username: 'security', password: 'guard123' },
    { label: 'Employee (a visitor expected today, pass VP-100001)', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { SECURITY_ADMIN: 'Security', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { SECURITY_ADMIN: '/gate', EMPLOYEE: '/invitations' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [{ to: '/invitations', label: 'My visitors' }],
    SECURITY_ADMIN: [
      { to: '/gate', label: 'Gate', end: true },
      { to: '/gate/reports', label: 'Daily report' },
    ],
  } as Record<Role, NavItem[]>,
}
