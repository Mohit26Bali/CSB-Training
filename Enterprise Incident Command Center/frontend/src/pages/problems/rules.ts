/**
 * What the problem screens offer to whom, and when. Which step may be pressed now comes from the server
 * (problem.availableActions, one rulebook in the backend, D31). This file only adds who may press it and the words.
 * The server checks everything again.
 */
import type { ActionStatus, Problem, ProblemStatus, ProblemStep, ResolutionCode } from '../../api/problems';
import type { AuthUser } from '../../auth/storage';

/** The six steps of the status bar, in order. */
export const PROBLEM_STATUSES: ProblemStatus[] = ['NEW', 'ASSESS', 'ROOT_CAUSE_ANALYSIS', 'FIX_IN_PROGRESS', 'RESOLVED', 'CLOSED'];

export const PROBLEM_STATUS_LABEL: Record<ProblemStatus, string> = {
  NEW: 'New',
  ASSESS: 'Assess',
  ROOT_CAUSE_ANALYSIS: 'Root cause analysis',
  FIX_IN_PROGRESS: 'Fix in progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

export const RESOLUTION_LABEL: Record<ResolutionCode, string> = {
  FIX_APPLIED: 'Fix applied',
  RISK_ACCEPTED: 'Risk accepted',
  CANCELED: 'Canceled',
  DUPLICATE: 'Duplicate',
};

/** The text of each button. */
export const STEP_LABEL: Record<ProblemStep, string> = {
  ASSESS: 'Assess',
  CONFIRM: 'Confirm',
  FIX: 'Fix',
  RESOLVE: 'Resolve',
  COMPLETE: 'Complete',
  ACCEPT_RISK: 'Accept risk',
  MARK_DUPLICATE: 'Mark duplicate',
  CANCEL: 'Cancel',
  RE_ANALYZE: 'Re-analyze',
};

/** The buttons that carry the problem forward are filled; the exits and the way back are outlined. */
export const FORWARD_STEPS: ProblemStep[] = ['ASSESS', 'CONFIRM', 'FIX', 'RESOLVE', 'COMPLETE'];

export const ACTION_STATUS_LABEL: Record<ActionStatus, string> = { OPEN: 'Open', DONE: 'Done' };

/** Engineers and admins can create problems and read them. */
export const canCreateProblem = (user: AuthUser) => user.role !== 'EMPLOYEE';

/** Only an administrator moves a problem, edits it and manages the actions. */
export const isAdmin = (user: AuthUser) => user.role === 'ADMIN';

/** The steps this user may press now: the server's list, for an administrator only. */
export const stepsFor = (user: AuthUser, problem: Problem): ProblemStep[] => (isAdmin(user) ? problem.availableActions : []);

/** The form fields can be edited until the problem is closed. */
export const canEditProblem = (user: AuthUser, problem: Problem) => isAdmin(user) && problem.status !== 'CLOSED';

/** No actions can be added to a closed problem. */
export const canAddAction = (user: AuthUser, problem: Problem) => isAdmin(user) && problem.status !== 'CLOSED';
