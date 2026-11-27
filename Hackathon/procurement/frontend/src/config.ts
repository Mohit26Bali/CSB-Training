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
  appName: 'Procurement Portal',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M3 3h2l.4 2m0 0L7 13h10l3-8H5.4z', 'M9 19.5a1 1 0 100 .01M17 19.5a1 1 0 100 .01'],
  headline: { line1: 'Office buying,', lead: 'made', words: ['simple', 'accountable', 'transparent', 'on budget'] },
  intro:
    'Ask for the equipment and software your team needs, see who approved it and follow it all the way to delivery. Managers keep spending within the monthly budget.',
  chips: ['Hardware', 'Software', 'Furniture', 'Office supplies', 'Services'],
  steps: [
    { title: 'Request an item', text: 'Say what you need, how many, the estimated cost and why. The total is worked out for you.', icon: 'M12 4v16m8-8H4' },
    { title: 'Manager decides', text: 'Procurement approves within the monthly budget, or rejects with a reason you can read.', icon: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
    { title: 'Follow it through', text: 'Track each request from approved to ordered to delivered, with the order reference.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to request and track purchases',
  registerSubtitle: 'Employee accounts only. Procurement managers are set up by Finance.',
  demoAccounts: [
    { label: 'Procurement manager', username: 'procurement', password: 'manager123' },
    { label: 'Employee', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { PROCUREMENT_MANAGER: 'Procurement manager', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { PROCUREMENT_MANAGER: '/manager', EMPLOYEE: '/requests' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [{ to: '/requests', label: 'My requests' }],
    PROCUREMENT_MANAGER: [
      { to: '/manager', label: 'Dashboard', end: true },
      { to: '/manager/review', label: 'Review requests' },
    ],
  } as Record<Role, NavItem[]>,
}
