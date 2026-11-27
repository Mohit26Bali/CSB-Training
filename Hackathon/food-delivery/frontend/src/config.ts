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
  appName: 'Spice Route',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M4 11h16a8 8 0 01-16 0z', 'M3 11h18', 'M9 4c0 2 2 2 2 4M13 4c0 2 2 2 2 4'],
  headline: { line1: 'Hot food,', lead: 'right to your', words: ['door', 'table', 'desk', 'couch'] },
  intro:
    'Pick from the menu, fill your cart and place the order. Watch it go from accepted to preparing to out the door, with the total worked out before you pay.',
  chips: ['Starters', 'Mains', 'Breads', 'Desserts'],
  steps: [
    { title: 'Choose your dishes', text: 'Filter by category or vegetarian, search by name.', icon: 'M4 6h16M4 12h16M4 18h10' },
    { title: 'Place the order', text: 'The cart is saved, and the total shows tax and delivery.', icon: 'M3 3h2l.4 2M7 13h10l3-8H5.4M7 13L5.4 5M7 13l-2 4h13' },
    { title: 'Follow it live', text: 'Placed, accepted, preparing, ready, delivered.', icon: 'M3 12l5 5L21 7' },
  ] as Step[],
  loginSubtitle: 'Sign in to order',
  registerSubtitle: 'Customer accounts only. Restaurant staff are set up by the owner.',
  demoAccounts: [
    { label: 'Restaurant admin', username: 'restaurant', password: 'admin123' },
    { label: 'Customer (orders in every state)', username: 'aarav.sharma', password: 'customer123' },
  ] as DemoAccount[],
  roleLabels: { RESTAURANT_ADMIN: 'Restaurant', CUSTOMER: 'Customer' } as Record<Role, string>,
  homePath: { RESTAURANT_ADMIN: '/admin', CUSTOMER: '/menu' } as Record<Role, string>,
  nav: {
    CUSTOMER: [
      { to: '/menu', label: 'Menu' },
      { to: '/orders', label: 'My orders' },
    ],
    RESTAURANT_ADMIN: [
      { to: '/admin', label: 'Orders', end: true },
      { to: '/admin/menu', label: 'Menu' },
      { to: '/admin/sales', label: 'Sales' },
    ],
  } as Record<Role, NavItem[]>,
}
