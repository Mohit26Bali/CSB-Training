/**
 * What the incident screens offer to whom. These mirror the backend rules (ARCHITECTURE, D22) only to decide which
 * buttons to show. The server checks every action again and answers 403 or 409 with a message that the screens show.
 */
import type { Incident, IncidentScope, IncidentState } from '../../api/incidents';
import type { Role } from '../../auth/access';
import type { AuthUser } from '../../auth/storage';

/**
 * The six categories. They are checked by the backend (IncidentCategories) but are not part of the generated contract,
 * because the backend declares the field as plain text. Keep this list in step with the backend and with the
 * Assignment rules (FRONTEND-PLAN, findings from F0).
 */
export const CATEGORIES = ['Hardware', 'Software', 'Network', 'Database', 'Access', 'Other'] as const;

export const STATES: IncidentState[] = ['NEW', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED'];

export const STATE_LABEL: Record<IncidentState, string> = {
  NEW: 'New',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};

/** 1 is the most serious for both impact and urgency (DATA-MODEL). */
export const LEVELS = [
  { value: 1, label: '1 - High' },
  { value: 2, label: '2 - Medium' },
  { value: 3, label: '3 - Low' },
];

export const PRIORITY_LABEL: Record<number, string> = { 1: 'Critical', 2: 'High', 3: 'Moderate', 4: 'Low', 5: 'Planning' };

/** The server calculates this and ignores any value sent. It is shown in the report form only as a preview. */
export const priorityFor = (impact: number, urgency: number): number => impact + urgency - 1;

export const SCOPE_LABEL: Record<IncidentScope, string> = {
  mine: 'Reported by me',
  assigned: 'My queue',
  all: 'All incidents',
};

/** The scopes each role may ask for. The first one is the default. An employee may only see their own. */
export const SCOPES: Record<Role, IncidentScope[]> = {
  EMPLOYEE: ['mine'],
  SUPPORT_ENGINEER: ['assigned', 'all', 'mine'],
  ADMIN: ['all', 'mine'],
};

export const LIST_TITLE: Record<Role, string> = { EMPLOYEE: 'My incidents', SUPPORT_ENGINEER: 'Incident queue', ADMIN: 'All incidents' };

const isEngineer = (user: AuthUser) => user.role === 'SUPPORT_ENGINEER';
export const isOpen = (incident: Incident) => incident.state === 'NEW' || incident.state === 'IN_PROGRESS' || incident.state === 'ON_HOLD';

/** Only an engineer of the team that owns the incident, and only while it is NEW. */
export const canAccept = (user: AuthUser, incident: Incident) => isEngineer(user) && incident.state === 'NEW' && user.teamId === incident.assignedTeamId;

/** Only the engineer who accepted it. */
export const canResolve = (user: AuthUser, incident: Incident) => isEngineer(user) && incident.state === 'IN_PROGRESS' && incident.assignedToId === user.id;

/** Only an engineer of the owning team, and only a RESOLVED incident. */
export const canClose = (user: AuthUser, incident: Incident) => isEngineer(user) && incident.state === 'RESOLVED' && user.teamId === incident.assignedTeamId;

/** Any engineer, while the incident is open (NEW, IN_PROGRESS or ON_HOLD). It goes back to NEW with nobody assigned. */
export const canReassign = (user: AuthUser, incident: Incident) => isEngineer(user) && isOpen(incident);

/** Engineers and admins, while the incident is open. */
export const canEdit = (user: AuthUser, incident: Incident) => (isEngineer(user) || user.role === 'ADMIN') && isOpen(incident);

/** A closed incident still takes comments (it can be reopened, D34). Only a cancelled one is final. */
export const canComment = (incident: Incident) => incident.state !== 'CANCELLED' && incident.state !== 'CLOSED';

/** The reporter, or an engineer of the owning team, while the incident is RESOLVED. It goes back to work. A CLOSED incident is final (D39). */
export const canReopen = (user: AuthUser, incident: Incident) =>
  incident.state === 'RESOLVED' &&
  ((user.role === 'EMPLOYEE' && incident.reporterId === user.id) || (isEngineer(user) && user.teamId === incident.assignedTeamId));

/** Employees may write comments only. Work notes are internal. */
export const canWriteWorkNote = (user: AuthUser) => user.role !== 'EMPLOYEE';

/**
 * The subcategories of each category (ServiceNow's lists, D32). Like CATEGORIES they are checked by the backend
 * (IncidentCategories) but are not part of the generated contract. Access and Other have none.
 */
export const SUBCATEGORIES: Record<string, string[]> = {
  Hardware: ['Memory', 'Mouse', 'Disk', 'Monitor', 'CPU', 'Keyboard'],
  Software: ['Email', 'Operating System'],
  Network: ['IP Address', 'VPN', 'DNS', 'Wireless', 'DHCP'],
  Database: ['MS SQL Server', 'DB2', 'Oracle'],
};

export const HOLD_REASONS = ['AWAITING_CALLER', 'AWAITING_PROBLEM', 'AWAITING_VENDOR', 'AWAITING_CHANGE'] as const;

export const HOLD_REASON_LABEL: Record<(typeof HOLD_REASONS)[number], string> = {
  AWAITING_CALLER: 'Awaiting caller',
  AWAITING_PROBLEM: 'Awaiting problem',
  AWAITING_VENDOR: 'Awaiting vendor',
  AWAITING_CHANGE: 'Awaiting change',
};

/** Only the engineer who is working on it, and only while it is IN_PROGRESS. */
export const canHold = (user: AuthUser, incident: Incident) => isEngineer(user) && incident.state === 'IN_PROGRESS' && incident.assignedToId === user.id;

/** Only the engineer who is working on it, and only while it is ON_HOLD. */
export const canResume = (user: AuthUser, incident: Incident) => isEngineer(user) && incident.state === 'ON_HOLD' && incident.assignedToId === user.id;

/** An engineer of the owning team, while the incident is open. A cancelled incident is final. */
export const canCancel = (user: AuthUser, incident: Incident) => isEngineer(user) && isOpen(incident) && user.teamId === incident.assignedTeamId;

/**
 * The ten resolution codes (D36): the close codes of the ServiceNow instance, spelled exactly as it spells them.
 * Like CATEGORIES they are checked by the backend (ResolutionCodes) but are not part of the generated contract.
 */
export const RESOLUTION_CODES = [
  'Duplicate',
  'Known error',
  'No resolution provided',
  'Resolved by caller',
  'Resolved by change',
  'Resolved by problem',
  'Resolved by request',
  'Solution provided',
  'Workaround provided',
  'User error',
] as const;
