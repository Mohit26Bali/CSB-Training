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
  appName: 'Event Hub',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M8 3v3M16 3v3M4 8h16', 'M5 5h14a1 1 0 011 1v13a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z', 'M9 14l2 2 4-4'],
  headline: { line1: 'Never miss', lead: 'the next', words: ['talk', 'workshop', 'match', 'celebration'] },
  intro:
    'Browse what is happening across the company, grab a seat in a click and see at a glance when an event is full. If it is, join the waitlist and we will move you up.',
  chips: ['Tech talks', 'Workshops', 'Sports', 'Cultural', 'Wellness', 'Town halls'],
  steps: [
    { title: 'Find an event', text: 'Search or filter upcoming events by category and see how many seats are left.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Reserve your seat', text: 'One click to register. Full event? Join the waitlist and keep your place in the queue.', icon: 'M12 4v16m8-8H4' },
    { title: 'Stay in the loop', text: 'Your registrations and waitlist positions in one place. Cancel any time before it starts.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to register for events',
  registerSubtitle: 'Employee accounts only. Event admins are set up by the events team.',
  demoAccounts: [
    { label: 'Event admin', username: 'eventadmin', password: 'admin123' },
    { label: 'Employee', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { EVENT_ADMIN: 'Event admin', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { EVENT_ADMIN: '/admin', EMPLOYEE: '/events' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [
      { to: '/events', label: 'Upcoming events' },
      { to: '/registrations', label: 'My registrations' },
    ],
    EVENT_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/events', label: 'Events' },
    ],
  } as Record<Role, NavItem[]>,
}
