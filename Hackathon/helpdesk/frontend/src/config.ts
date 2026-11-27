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
  appName: 'IT Help Desk',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 12a8 8 0 0116 0', 'M4 12v3a2 2 0 002 2h1v-5H6a2 2 0 00-2 2z', 'M20 12v3a2 2 0 01-2 2h-1v-5h1a2 2 0 012 2z', 'M17 17v1a3 3 0 01-3 3h-2'],
  headline: { line1: 'Tech trouble?', lead: "We'll get you", words: ['working', 'connected', 'moving', 'back'] },
  intro:
    'Tell us what is wrong in a minute, see who picked it up and follow it to the fix. Every ticket has a clear deadline, so nothing sits unseen.',
  chips: ['Hardware', 'Software', 'Network', 'Access', 'Email'],
  steps: [
    { title: 'Raise a ticket', text: 'Say what happened, pick a category and how urgent it is.', icon: 'M12 4v16m8-8H4' },
    { title: 'An agent picks it up', text: 'The most urgent tickets are handled first, against a deadline for each priority.', icon: 'M16 11a4 4 0 100-8 4 4 0 000 8zM4 21v-1a6 6 0 0112 0v1' },
    { title: 'Fixed and confirmed', text: 'You see every update, the resolution and can reply right on the ticket.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to raise and follow your tickets',
  registerSubtitle: 'Employee accounts only. Support agents are set up by IT.',
  demoAccounts: [
    { label: 'Support agent', username: 'support', password: 'agent123' },
    { label: 'Employee (a ticket in every state)', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { SUPPORT_AGENT: 'Support agent', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { SUPPORT_AGENT: '/agent', EMPLOYEE: '/tickets' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [{ to: '/tickets', label: 'My tickets' }],
    SUPPORT_AGENT: [
      { to: '/agent', label: 'Dashboard', end: true },
      { to: '/agent/queue', label: 'Ticket queue' },
    ],
  } as Record<Role, NavItem[]>,
}
