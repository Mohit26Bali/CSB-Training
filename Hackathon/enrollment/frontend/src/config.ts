import type { Role } from './api/types'

/** Branding, login copy and navigation, read by the shared AuthShell and Layout (the same shape the other hackathon apps use). */

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

export const config = {
  appName: 'Course Enrollment',
  /** SVG path(s), 24x24, for the logo mark */
  logo: ['M12 4l9 4-9 4-9-4 9-4z', 'M7 10.5V15c0 1.4 2.2 3 5 3s5-1.6 5-3v-4.5', 'M21 8v6'],
  headline: { line1: 'Grow your skills in', lead: '', words: ['programming', 'cloud', 'data science', 'leadership', 'security', 'design'] },
  intro: 'Hands-on internal training, taught by your own colleagues. Pick a course, request your seat and keep track of it all in one place.',
  chips: ['Programming', 'Cloud', 'Data', 'DevOps', 'Management', 'Soft Skills', 'Compliance', 'Design'],
  steps: [
    { title: 'Browse the catalogue', text: 'Search by topic or filter by category and see how many seats are left.', icon: 'M21 21l-4.3-4.3M17 10a7 7 0 11-14 0 7 7 0 0114 0z' },
    { title: 'Request a seat', text: 'One click sends your request. Cancel it any time while it is pending.', icon: 'M12 4v16m8-8H4' },
    { title: 'Get approved', text: 'An admin reviews each request. Your status updates as soon as they decide.', icon: 'M5 13l4 4L19 7' },
  ] as Step[],
  roleLabels: { ADMIN: 'Administrator', EMPLOYEE: 'Employee' } as Record<Role, string>,
  nav: {
    EMPLOYEE: [
      { to: '/courses', label: 'Browse courses' },
      { to: '/my-enrollments', label: 'My enrollments' },
    ],
    ADMIN: [
      { to: '/admin', label: 'Dashboard', end: true },
      { to: '/admin/courses', label: 'Courses' },
      { to: '/admin/requests', label: 'Requests' },
    ],
  } as Record<Role, NavItem[]>,
}
