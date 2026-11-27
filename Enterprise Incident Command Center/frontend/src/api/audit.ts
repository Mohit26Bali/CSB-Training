/**
 * The audit trails. Each service keeps its own (D11), so there are four endpoints and the screen shows one tab per service.
 * They all return the same shape: a page of events, newest first.
 */
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { assignmentApi, incidentApi, majorIncidentApi, rcaApi, unwrap } from './client';
import type { components } from './generated/incident';

export type AuditEvent = components['schemas']['AuditEventResponse'];
export type AuditSource = 'incident' | 'major' | 'problem' | 'assignment';

export const AUDIT_SOURCES: { key: AuditSource; label: string }[] = [
  { key: 'incident', label: 'Incidents' },
  { key: 'major', label: 'Major incidents' },
  { key: 'problem', label: 'Problems' },
  { key: 'assignment', label: 'Users and rules' },
];

function load(source: AuditSource, page: number, size: number) {
  const params = { params: { query: { page, size } } };
  switch (source) {
    case 'incident':
      return unwrap(incidentApi.GET('/api/incidents/audit', params));
    case 'major':
      return unwrap(majorIncidentApi.GET('/api/major-incidents/audit', params));
    case 'problem':
      return unwrap(rcaApi.GET('/api/problems/audit', params));
    case 'assignment':
      return unwrap(assignmentApi.GET('/api/assignment/audit', params));
  }
}

export function useAudit(source: AuditSource, page: number, size: number) {
  return useQuery({
    queryKey: ['audit', source, page, size],
    queryFn: () => load(source, page, size),
    placeholderData: keepPreviousData,
  });
}
