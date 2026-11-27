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
  appName: 'ParkEasy',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M5 3h8a5 5 0 010 10H9v8', 'M5 3v18'],
  headline: { line1: 'Your spot,', lead: 'always', words: ['reserved', 'covered', 'close by', 'ready'] },
  intro:
    'See which slots are free on every floor, send a request with your vehicle number and get a slot of your own. The parking desk sees the whole lot on one map.',
  chips: ['Ground floor', 'Floor 1', 'Floor 2', 'Floor 3'],
  steps: [
    { title: 'Check the map', text: 'Free slots are green. Pick a floor you like.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Send a request', text: 'Add your vehicle number and an optional preferred floor.', icon: 'M12 4v16m8-8H4' },
    { title: 'Park', text: 'When the admin approves, your slot and vehicle show up on My parking.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to find and manage your parking slot',
  registerSubtitle: 'Employee accounts only. Parking admins are set up by facilities.',
  demoAccounts: [
    { label: 'Parking admin', username: 'parkadmin', password: 'admin123' },
    { label: 'Employee (no slot yet)', username: 'aarav.sharma', password: 'employee123' },
    { label: 'Employee (has a slot)', username: 'diya.patel', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { PARKING_ADMIN: 'Parking admin', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { PARKING_ADMIN: '/admin', EMPLOYEE: '/my-parking' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [
      { to: '/my-parking', label: 'My parking' },
      { to: '/slots', label: 'Free slots' },
    ],
    PARKING_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/requests', label: 'Requests' },
      { to: '/admin/slots', label: 'Slot map' },
      { to: '/admin/allocations', label: 'Allocations' },
    ],
  } as Record<Role, NavItem[]>,
}
