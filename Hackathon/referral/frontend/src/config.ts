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
  appName: 'Referral Portal',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M16 11a4 4 0 100-8 4 4 0 000 8z', 'M3 21v-1a6 6 0 0112 0v1', 'M17 14.5a5 5 0 014 4.5v2'],
  headline: { line1: 'Great people know', lead: 'great', words: ['engineers', 'designers', 'analysts', 'colleagues'] },
  intro:
    'Spot an open role that fits someone you know, refer them in a minute and follow their progress from screening to interview to offer.',
  chips: ['Engineering', 'Design', 'Analytics', 'Quality', 'People', 'Marketing'],
  steps: [
    { title: 'Find a role', text: 'Search the open positions by title or by the skills you know your friend has.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Refer a candidate', text: 'Share their name and email, and tell us why they would be great. Duplicates are caught for you.', icon: 'M12 4v16m8-8H4' },
    { title: 'Follow the progress', text: 'See when the recruiter screens them, schedules an interview or makes an offer.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to refer candidates and follow their progress',
  registerSubtitle: 'Employee accounts only. Recruiters are set up by HR.',
  demoAccounts: [
    { label: 'Recruiter', username: 'recruiter', password: 'recruiter123' },
    { label: 'Employee', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { RECRUITER: 'Recruiter', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { RECRUITER: '/recruiter', EMPLOYEE: '/jobs' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [
      { to: '/jobs', label: 'Open positions' },
      { to: '/referrals', label: 'My referrals' },
    ],
    RECRUITER: [
      { to: '/recruiter', label: 'Dashboard', end: true },
      { to: '/recruiter/referrals', label: 'Referrals' },
      { to: '/recruiter/jobs', label: 'Jobs' },
    ],
  } as Record<Role, NavItem[]>,
}
