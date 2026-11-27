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
  appName: 'Slotwise',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M8 7V3m8 4V3M4 11h16M5 5h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z', 'M9 16l2 2 4-4'],
  headline: { line1: 'The right time with', lead: 'the right', words: ['doctor', 'dentist', 'coach', 'expert'] },
  intro:
    'Browse providers, see their open slots on a calendar and book in two clicks. The provider confirms, and you can cancel any time up to two hours before.',
  chips: ['Doctors', 'Dentists', 'Physio', 'Coaching'],
  steps: [
    { title: 'Find a provider', text: 'Search by name, speciality or place.', icon: 'M21 21l-4.3-4.3M10 17a7 7 0 100-14 7 7 0 000 14z' },
    { title: 'Pick an open slot', text: 'Taken and past times are never offered.', icon: 'M12 8v4l3 2M12 21a9 9 0 100-18 9 9 0 000 18z' },
    { title: 'Get a confirmation', text: 'Follow it as pending, confirmed or cancelled.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to book and manage appointments',
  registerSubtitle: 'Customer accounts only. Providers are set up by the practice.',
  demoAccounts: [
    { label: 'Provider (Dr. Neha Mehta)', username: 'dr.mehta', password: 'provider123' },
    { label: 'Customer (appointments in every state)', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { SERVICE_PROVIDER: 'Provider', CUSTOMER: 'Customer' } as Record<Role, string>,
  homePath: { SERVICE_PROVIDER: '/provider', CUSTOMER: '/providers' } as Record<Role, string>,
  nav: {
    CUSTOMER: [
      { to: '/providers', label: 'Find a provider' },
      { to: '/appointments', label: 'My appointments' },
    ],
    SERVICE_PROVIDER: [
      { to: '/provider', label: 'Dashboard', end: true },
      { to: '/provider/calendar', label: 'Calendar' },
      { to: '/provider/appointments', label: 'Appointments' },
      { to: '/provider/profile', label: 'Profile' },
    ],
  } as Record<Role, NavItem[]>,
}
