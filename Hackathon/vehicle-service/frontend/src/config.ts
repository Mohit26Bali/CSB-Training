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
  appName: 'AutoCare',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M14.7 6.3a4 4 0 00-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 005.4-5.4l-2.4 2.4-2.1-.6-.6-2.1 2.1-2.7z'],
  headline: { line1: 'Keep your vehicle', lead: 'running', words: ['smoothly', 'safely', 'longer', 'happily'] },
  intro:
    'Register your car or bike, pick a service and a day when a bay is free, and follow the work from booked to done. You see the estimate before you book.',
  chips: ['Cars', 'Bikes', 'SUVs', 'Vans'],
  steps: [
    { title: 'Add your vehicle', text: 'Registration number, make and model, once.', icon: 'M5 17h14M5 17a2 2 0 100 4 2 2 0 000-4zm14 0a2 2 0 100 4 2 2 0 000-4zM3 13l2-6h14l2 6M3 13v4m18-4v4' },
    { title: 'Pick a service and day', text: 'Only days with a free bay can be chosen.', icon: 'M8 7V3m8 4V3M4 11h16M5 5h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z' },
    { title: 'Follow the work', text: 'Booked, in service, completed, with the final bill.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to book and follow your services',
  registerSubtitle: 'Customer accounts only. Workshop staff are set up by the manager.',
  demoAccounts: [
    { label: 'Service admin', username: 'serviceadmin', password: 'admin123' },
    { label: 'Customer (a car and a bike, with history)', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { SERVICE_ADMIN: 'Service admin', CUSTOMER: 'Customer' } as Record<Role, string>,
  homePath: { SERVICE_ADMIN: '/admin', CUSTOMER: '/services' } as Record<Role, string>,
  nav: {
    CUSTOMER: [
      { to: '/services', label: 'My services' },
      { to: '/book', label: 'Book a service' },
      { to: '/vehicles', label: 'My vehicles' },
    ],
    SERVICE_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/schedule', label: 'Schedule' },
      { to: '/admin/vehicles', label: 'Vehicles' },
      { to: '/admin/services', label: 'Services' },
    ],
  } as Record<Role, NavItem[]>,
}
