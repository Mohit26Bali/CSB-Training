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
  appName: 'KitTrack',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 5h16v10H4z', 'M2 19h20', 'M9 15v4m6-4v4'],
  headline: { line1: 'Every laptop and screen,', lead: 'always', words: ['accounted for', 'traceable', 'returned', 'in good hands'] },
  intro:
    'See what equipment is assigned to you, hand it back with one click, and let IT keep a complete history of who held what and in which condition.',
  chips: ['Laptops', 'Monitors', 'Phones', 'Accessories'],
  steps: [
    { title: 'IT assigns it', text: 'Only available equipment is handed out, to one person at a time.', icon: 'M12 4v16m8-8H4' },
    { title: 'You use it', text: 'Your assets and due dates are listed on one page.', icon: 'M5 13l4 4L19 7' },
    { title: 'You hand it back', text: 'Raise a return request and IT checks it in.', icon: 'M3 10h11a5 5 0 010 10H9M3 10l4-4M3 10l4 4' },
  ] as Step[],
  loginSubtitle: 'Sign in to see your equipment',
  registerSubtitle: 'Employee accounts only. IT staff are set up by the administrator.',
  demoAccounts: [
    { label: 'Asset admin (IT)', username: 'assetadmin', password: 'admin123' },
    { label: 'Employee (holds a laptop and a monitor)', username: 'aarav.sharma', password: 'employee123' },
  ] as DemoAccount[],
  roleLabels: { ASSET_ADMIN: 'Asset admin', EMPLOYEE: 'Employee' } as Record<Role, string>,
  homePath: { ASSET_ADMIN: '/admin', EMPLOYEE: '/my-assets' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [
      { to: '/my-assets', label: 'My equipment' },
      { to: '/history', label: 'History' },
    ],
    ASSET_ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/assets', label: 'Assets' },
      { to: '/admin/returns', label: 'Returns' },
    ],
  } as Record<Role, NavItem[]>,
}
