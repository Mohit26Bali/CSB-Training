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
  appName: 'Community Care',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M14.7 6.3a4 4 0 00-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 005.4-5.4l-2.6 2.6-2.4-.6-.6-2.4 2.6-2.6z'],
  headline: { line1: 'Something broken at', lead: 'home? Get it', words: ['fixed', 'looked at', 'sorted', 'repaired'] },
  intro:
    'Report a leak, a flickering light or a stuck lift in a minute. See who is assigned, how long it has been waiting and what was done when it is finished.',
  chips: ['Plumbing', 'Electrical', 'Lifts', 'Cleaning', 'Garden', 'Security'],
  steps: [
    { title: 'Report it', text: 'Say what is wrong and where, and how urgent it is.', icon: 'M12 4v16m8-8H4' },
    { title: 'A technician is assigned', text: 'The facility manager picks the right person and you can see who.', icon: 'M16 11a4 4 0 100-8 4 4 0 000 8zM4 21v-1a6 6 0 0112 0v1' },
    { title: 'Read what was done', text: 'Every finished job comes with resolution notes.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to report and follow maintenance requests',
  registerSubtitle: 'Resident accounts only. Facility managers are set up by the society office.',
  demoAccounts: [
    { label: 'Facility manager', username: 'facility', password: 'manager123' },
    { label: 'Resident (a request in every state)', username: 'aarav.sharma', password: 'resident123' },
  ] as DemoAccount[],
  roleLabels: { FACILITY_MANAGER: 'Facility manager', RESIDENT: 'Resident' } as Record<Role, string>,
  homePath: { FACILITY_MANAGER: '/manager', RESIDENT: '/requests' } as Record<Role, string>,
  nav: {
    RESIDENT: [{ to: '/requests', label: 'My requests' }],
    FACILITY_MANAGER: [
      { to: '/manager', label: 'Dashboard', end: true },
      { to: '/manager/requests', label: 'Requests' },
      { to: '/manager/technicians', label: 'Technicians' },
    ],
  } as Record<Role, NavItem[]>,
}
