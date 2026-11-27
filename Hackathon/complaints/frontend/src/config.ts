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
  appName: 'Complaint Desk',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 5h16v11H9l-5 4V5z', 'M8 9h8', 'M8 12h5'],
  headline: { line1: 'Something went wrong?', lead: "We'll make it", words: ['right', 'clear', 'better', 'simple'] },
  intro:
    'Tell us what happened and watch it move from filed to resolved on a timeline you can follow. Every complaint gets an owner and a written resolution.',
  chips: ['Billing', 'Product', 'Delivery', 'Service', 'Staff'],
  steps: [
    { title: 'File it', text: 'Describe the problem, pick a category and say how urgent it is.', icon: 'M12 4v16m8-8H4' },
    { title: 'We take ownership', text: 'A named agent picks it up and updates you on the timeline.', icon: 'M16 11a4 4 0 100-8 4 4 0 000 8zM4 21v-1a6 6 0 0112 0v1' },
    { title: 'Read the resolution', text: 'See exactly what was done, then rate how we handled it.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to file and follow your complaints',
  registerSubtitle: 'Customer accounts only. Support agents are set up by the service team.',
  demoAccounts: [
    { label: 'Support agent', username: 'agent', password: 'agent123' },
    { label: 'Customer (a complaint in every state)', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { SUPPORT_AGENT: 'Support agent', CUSTOMER: 'Customer' } as Record<Role, string>,
  homePath: { SUPPORT_AGENT: '/agent', CUSTOMER: '/complaints' } as Record<Role, string>,
  nav: {
    CUSTOMER: [{ to: '/complaints', label: 'My complaints' }],
    SUPPORT_AGENT: [
      { to: '/agent', label: 'Statistics', end: true },
      { to: '/agent/queue', label: 'Complaint queue' },
    ],
  } as Record<Role, NavItem[]>,
}
