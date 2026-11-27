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
  appName: 'Hostel Desk',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M3 21V10l9-6 9 6v11', 'M9 21v-6h6v6', 'M3 21h18'],
  headline: { line1: 'Find your', lead: 'place to', words: ['study', 'rest', 'belong', 'live'] },
  intro:
    'See which rooms have a free bed, send a request in a minute and follow it from the waiting list to your key. Wardens see every bed on one board.',
  chips: ['Single rooms', 'Double rooms', 'Triple rooms', 'Dormitories'],
  steps: [
    { title: 'Browse free rooms', text: 'Filter by room type and see how many beds are left on each floor.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Send a request', text: 'Tell the warden what you prefer. You keep your place in the waiting list.', icon: 'M12 4v16m8-8H4' },
    { title: 'Move in', text: 'When the warden approves, your room shows up here with the floor and room type.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to request and manage your room',
  registerSubtitle: 'Student accounts only. Wardens are set up by the hostel office.',
  demoAccounts: [
    { label: 'Warden', username: 'warden', password: 'admin123' },
    { label: 'Student (no room yet)', username: 'aarav.sharma', password: 'student123' },
    { label: 'Student (has a room)', username: 'diya.patel', password: 'student123' },
  ] as DemoAccount[],
  roleLabels: { HOSTEL_ADMIN: 'Warden', STUDENT: 'Student' } as Record<Role, string>,
  homePath: { HOSTEL_ADMIN: '/admin', STUDENT: '/my-room' } as Record<Role, string>,
  nav: {
    STUDENT: [
      { to: '/my-room', label: 'My room' },
      { to: '/rooms', label: 'Available rooms' },
    ],
    HOSTEL_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/requests', label: 'Requests' },
      { to: '/admin/rooms', label: 'Rooms' },
      { to: '/admin/residents', label: 'Residents' },
    ],
  } as Record<Role, NavItem[]>,
}
