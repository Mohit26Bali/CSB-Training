import { Alert, Box, Chip } from '@mui/material';
import { ApiError } from '../api/errors';
import type { IncidentState } from '../api/incidents';
import type { MajorStatus } from '../api/majorIncidents';
import type { ActionStatus, ProblemStatus } from '../api/problems';
import { MAJOR_STATUS_LABEL } from '../pages/major/rules';
import { ACTION_STATUS_LABEL, PROBLEM_STATUS_LABEL } from '../pages/problems/rules';
import { PRIORITY_LABEL, STATE_LABEL } from '../pages/incidents/rules';

/** Shows the message of a failed call. A 503 (a service is down) is a warning because trying again may work. */
export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;
  const apiError = error instanceof ApiError ? error : null;
  const message = error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <Alert severity={apiError?.isUnavailable ? 'warning' : 'error'} sx={{ mb: 2 }}>
      {message}
      {apiError?.correlationId ? ` (reference ${apiError.correlationId})` : ''}
    </Alert>
  );
}

const STATE_COLOR: Record<IncidentState, 'info' | 'warning' | 'success' | 'secondary' | 'default'> = {
  NEW: 'info',
  IN_PROGRESS: 'warning',
  ON_HOLD: 'secondary',
  RESOLVED: 'success',
  CLOSED: 'default',
  CANCELLED: 'default',
};

export function StateChip({ state }: { state: IncidentState }) {
  return <Chip size="small" label={STATE_LABEL[state]} color={STATE_COLOR[state]} variant={state === 'CLOSED' || state === 'CANCELLED' ? 'outlined' : 'filled'} />;
}

const PRIORITY_COLOR: Record<number, 'error' | 'warning' | 'info' | 'default'> = { 1: 'error', 2: 'warning', 3: 'info', 4: 'default', 5: 'default' };

export function PriorityChip({ priority }: { priority: number }) {
  return <Chip size="small" label={`P${priority} ${PRIORITY_LABEL[priority] ?? ''}`.trim()} color={PRIORITY_COLOR[priority] ?? 'default'} variant={priority <= 2 ? 'filled' : 'outlined'} />;
}

/** "5m ago", "3h ago", "29d ago": how long ago, for lists where the exact time is in the tooltip. */
export function timeAgo(value: string | null | undefined, now: number = Date.now()): string {
  if (!value) return '-';
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return value;
  const minutes = Math.max(0, Math.floor((now - then) / 60000));
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}h ago`;
  return `${Math.floor(minutes / 1440)}d ago`;
}

/** Priority in a list: a red dot for Critical and High, plain text for the rest (the colour alone never carries the meaning). */
export function PriorityDot({ priority }: { priority: number }) {
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, whiteSpace: 'nowrap' }}>
      {priority <= 2 && <Box component="span" aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: priority === 1 ? 'error.main' : 'warning.main' }} />}
      {`${priority} - ${PRIORITY_LABEL[priority] ?? ''}`}
    </Box>
  );
}

/** The backend sends dates without a time zone (local server time), so they are shown as they are: 2026-11-24 16:41:06. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`;
}

const MAJOR_COLOR: Record<MajorStatus, 'error' | 'warning' | 'success' | 'default'> = {
  DECLARED: 'error',
  MITIGATING: 'warning',
  RESOLVED: 'success',
  CLOSED: 'default',
};

export function MajorStatusChip({ status }: { status: MajorStatus }) {
  return <Chip size="small" label={MAJOR_STATUS_LABEL[status]} color={MAJOR_COLOR[status]} variant={status === 'CLOSED' ? 'outlined' : 'filled'} />;
}

const PROBLEM_COLOR: Record<ProblemStatus, 'info' | 'warning' | 'success' | 'default'> = {
  NEW: 'info',
  ASSESS: 'info',
  ROOT_CAUSE_ANALYSIS: 'warning',
  FIX_IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  CLOSED: 'default',
};

export function ProblemStatusChip({ status }: { status: ProblemStatus }) {
  return <Chip size="small" label={PROBLEM_STATUS_LABEL[status]} color={PROBLEM_COLOR[status]} variant={status === 'CLOSED' ? 'outlined' : 'filled'} />;
}

export function ActionStatusChip({ status }: { status: ActionStatus }) {
  return <Chip size="small" label={ACTION_STATUS_LABEL[status]} color={status === 'DONE' ? 'success' : 'warning'} variant={status === 'DONE' ? 'filled' : 'outlined'} />;
}
