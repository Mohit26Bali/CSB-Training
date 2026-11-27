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
  appName: 'Warranty Claims',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z', 'M9 12l2 2 4-4'],
  headline: { line1: 'Warranty claims,', lead: 'made', words: ['simple', 'quick', 'transparent', 'painless'] },
  intro:
    'Register what you have bought, see exactly how long each warranty lasts and file a claim in a minute. Follow it from approval to repair to completion.',
  chips: ['Laptops', 'Phones', 'Televisions', 'Appliances', 'Audio', 'Wearables'],
  steps: [
    { title: 'Register your product', text: 'Add the serial number and purchase date. We work out when the warranty ends.', icon: 'M12 4v16m8-8H4' },
    { title: 'File a claim', text: 'Describe the problem and set a priority. Claims are only possible while you are covered.', icon: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
    { title: 'Follow the repair', text: 'See when it is approved, in repair and completed, and read what the engineer did.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to manage your products and claims',
  registerSubtitle: 'Customer accounts only. Service agents are set up by the service centre.',
  demoAccounts: [
    { label: 'Service agent', username: 'agent', password: 'agent123' },
    { label: 'Customer', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { SERVICE_AGENT: 'Service agent', CUSTOMER: 'Customer' } as Record<Role, string>,
  homePath: { SERVICE_AGENT: '/agent', CUSTOMER: '/products' } as Record<Role, string>,
  nav: {
    CUSTOMER: [
      { to: '/products', label: 'My products' },
      { to: '/claims', label: 'My claims' },
    ],
    SERVICE_AGENT: [{ to: '/agent', label: 'Claims queue' }],
  } as Record<Role, NavItem[]>,
}
