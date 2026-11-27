/**
 * What the major incident screens offer. These mirror the backend rules (ARCHITECTURE, D22) only to decide which
 * buttons to show; the server checks every action again and answers 403 or 409 with a message the screens display.
 */
import type { Incident } from '../../api/incidents';
import type { MajorStatus } from '../../api/majorIncidents';
import type { AuthUser } from '../../auth/storage';

/** The only order allowed: one step at a time, never back. */
export const MAJOR_STATUSES: MajorStatus[] = ['DECLARED', 'MITIGATING', 'RESOLVED', 'CLOSED'];

export const MAJOR_STATUS_LABEL: Record<MajorStatus, string> = {
  DECLARED: 'Declared',
  MITIGATING: 'Mitigating',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/** The one status this one may move to, or null when it is the last. */
export const nextStatus = (status: MajorStatus): MajorStatus | null => MAJOR_STATUSES[MAJOR_STATUSES.indexOf(status) + 1] ?? null;

/** Engineers and admins run major incidents. Employees have no access to these screens at all. */
export const canManageMajor = (user: AuthUser) => user.role !== 'EMPLOYEE';

/** No updates after CLOSED. */
export const canAddUpdate = (status: MajorStatus) => status !== 'CLOSED';

/**
 * Only a priority 1 incident that has no major incident yet can be escalated, by an engineer or an admin.
 * A closed or cancelled incident is not offered (the server has no state rule, but escalating a finished incident makes no sense).
 */
export const canDeclare = (user: AuthUser, incident: Incident) =>
  canManageMajor(user) && incident.priority === 1 && incident.majorIncidentId == null && incident.state !== 'CLOSED' && incident.state !== 'CANCELLED';
