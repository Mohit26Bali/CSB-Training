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
  appName: 'Grand Stay',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M3 21V8l9-5 9 5v13', 'M9 21v-6h6v6', 'M3 21h18', 'M12 9v2'],
  headline: { line1: 'Your next stay,', lead: 'a few clicks', words: ['away', 'ready', 'waiting', 'booked'] },
  intro:
    'Pick your dates, see exactly which rooms are free and what the whole stay will cost, and book in a minute. The hotel confirms and you can follow it here.',
  chips: ['Standard', 'Deluxe', 'Suites', 'Family rooms'],
  steps: [
    { title: 'Choose your dates', text: 'We show only the rooms that are really free, with the total for your stay.', icon: 'M8 7V3m8 4V3M4 11h16M5 5h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z' },
    { title: 'Request a room', text: 'Add any special requests. The hotel confirms your booking.', icon: 'M12 4v16m8-8H4' },
    { title: 'Arrive and relax', text: 'Cancel free of charge any time before the check-in date.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to book rooms and manage your stays',
  registerSubtitle: 'Guest accounts only. Hotel staff are set up by the manager.',
  demoAccounts: [
    { label: 'Hotel admin', username: 'hoteladmin', password: 'admin123' },
    { label: 'Guest (bookings in every state)', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { HOTEL_ADMIN: 'Hotel admin', CUSTOMER: 'Guest' } as Record<Role, string>,
  homePath: { HOTEL_ADMIN: '/admin', CUSTOMER: '/rooms' } as Record<Role, string>,
  nav: {
    CUSTOMER: [
      { to: '/rooms', label: 'Find a room' },
      { to: '/bookings', label: 'My bookings' },
    ],
    HOTEL_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/bookings', label: 'Bookings' },
      { to: '/admin/calendar', label: 'Calendar' },
      { to: '/admin/rooms', label: 'Rooms' },
    ],
  } as Record<Role, NavItem[]>,
}
