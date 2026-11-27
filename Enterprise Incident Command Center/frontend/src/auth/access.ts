/**
 * Who may open which page, and which menu each role sees. One table, used by the menu and by the route guards,
 * so the two can never disagree.
 *
 * The backend stays the authority: it answers 403 or 404 for anything a role may not do. This table only decides
 * what the interface offers. The roles are the contract's own values (generated from the Assignment service).
 */
import type { components } from '../api/generated/assignment';

export type Role = NonNullable<components['schemas']['UserResponse']['role']>;

export const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  SUPPORT_ENGINEER: 'Support engineer',
  ADMIN: 'Administrator',
};

export interface NavItem {
  path: string;
  label: string;
  /** false while the screen is not built yet (F2 and later): the menu shows it as "soon" and does not open it. */
  ready: boolean;
}

const home: NavItem = { path: '/', label: 'Home', ready: true };

const MENU: Record<Role, NavItem[]> = {
  EMPLOYEE: [
    home,
    { path: '/incidents', label: 'My incidents', ready: true },
    { path: '/incidents/new', label: 'Report an incident', ready: true },
  ],
  SUPPORT_ENGINEER: [
    home,
    { path: '/incidents', label: 'Incident queue', ready: true },
    { path: '/major-incidents', label: 'Major incidents', ready: true },
    { path: '/problems', label: 'Problems', ready: true },
  ],
  ADMIN: [
    home,
    { path: '/dashboard', label: 'Dashboard', ready: true },
    { path: '/incidents', label: 'All incidents', ready: true },
    { path: '/major-incidents', label: 'Major incidents', ready: true },
    { path: '/problems', label: 'Problems and RCA', ready: true },
    { path: '/admin/rules', label: 'Assignment rules', ready: true },
    { path: '/admin/audit', label: 'Audit trails', ready: true },
  ],
};

export const menuFor = (role: Role): NavItem[] => MENU[role];

/** Every page except Home, with the roles that have it in their menu. Used to build the guarded routes. */
export function guardedPages(): { path: string; label: string; roles: Role[] }[] {
  const pages = new Map<string, { path: string; label: string; roles: Role[] }>();
  for (const role of Object.keys(MENU) as Role[]) {
    for (const item of MENU[role]) {
      if (item.path === '/') continue;
      const page = pages.get(item.path) ?? { path: item.path, label: item.label, roles: [] };
      page.roles.push(role);
      pages.set(item.path, page);
    }
  }
  return [...pages.values()];
}

/** What each role can do, in plain words, for the home page. */
export const ROLE_ABILITIES: Record<Role, string[]> = {
  EMPLOYEE: ['Report an incident', 'Follow your own incidents', 'Add comments to your incidents'],
  SUPPORT_ENGINEER: [
    'Work the queue of your team: accept, add work notes, resolve, close, reassign',
    'Escalate a priority 1 incident to a major incident and run it',
    'Create problems',
  ],
  ADMIN: ['See the dashboard and the audit trails', 'Record the root cause (RCA) and corrective actions', 'Change the assignment rules', 'Everything an engineer can do except accepting incidents'],
};
