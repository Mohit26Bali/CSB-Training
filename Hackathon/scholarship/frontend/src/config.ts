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
  appName: 'Scholarship Portal',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M22 10L12 5 2 10l10 5 10-5z', 'M6 12v5c3 2.5 9 2.5 12 0v-5'],
  headline: { line1: 'Funding your future,', lead: 'made', words: ['clear', 'fair', 'simple', 'transparent'] },
  intro:
    'Browse the scholarships you can apply for, submit your details once and follow your application to a decision. Every decision comes with a reason.',
  chips: ['Merit', 'Women in STEM', 'First generation', 'Rural talent', 'Sports', 'Research'],
  steps: [
    { title: 'Find a scholarship', text: 'See the amount, the deadline and exactly what the criteria are.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Apply once', text: 'Share your CGPA, family income and a short statement. You see your eligibility score straight away.', icon: 'M12 4v16m8-8H4' },
    { title: 'Track the decision', text: 'Your application status updates as soon as the committee decides, with the reason.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to apply and track your applications',
  registerSubtitle: 'Student accounts only. Administrators are set up by the scholarship office.',
  demoAccounts: [
    { label: 'Administrator', username: 'scholarship', password: 'admin123' },
    { label: 'Student', username: 'aarav.sharma', password: 'student123' },
  ] as DemoAccount[],
  roleLabels: { SCHOLARSHIP_ADMIN: 'Administrator', STUDENT: 'Student' } as Record<Role, string>,
  homePath: { SCHOLARSHIP_ADMIN: '/admin', STUDENT: '/programs' } as Record<Role, string>,
  nav: {
    STUDENT: [
      { to: '/programs', label: 'Scholarships' },
      { to: '/applications', label: 'My applications' },
    ],
    SCHOLARSHIP_ADMIN: [
      { to: '/admin', label: 'Statistics', end: true },
      { to: '/admin/applications', label: 'Applications' },
      { to: '/admin/programs', label: 'Scholarships' },
    ],
  } as Record<Role, NavItem[]>,
}
