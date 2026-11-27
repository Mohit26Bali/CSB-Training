/**
 * Everything the screens need from the RCA service: the problem list, one problem with its incidents and corrective
 * actions, and the actions (create, edit, the nine steps, add and finish corrective actions).
 * Types come from the generated contract (D24).
 */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { rcaApi, unwrap } from './client';
import type { components } from './generated/rca';

type Schemas = components['schemas'];
export type Problem = Schemas['ProblemResponse'];
export type ProblemStatus = Problem['status'];
/** One button of the problem form: ASSESS, CONFIRM, FIX, RESOLVE, COMPLETE, ACCEPT_RISK, MARK_DUPLICATE, CANCEL, RE_ANALYZE. */
export type ProblemStep = Problem['availableActions'][number];
export type ResolutionCode = NonNullable<Problem['resolutionCode']>;
export type ProblemAction = Schemas['ActionResponse'];
export type ActionStatus = ProblemAction['status'];
export type CreateProblemRequest = Schemas['CreateProblemRequest'];
export type UpdateProblemRequest = Schemas['UpdateProblemRequest'];
export type FixRequest = Schemas['FixRequest'];
export type AcceptRiskRequest = Schemas['AcceptRiskRequest'];
export type AddActionRequest = Schemas['AddActionRequest'];

export function useProblems(filters: { status?: ProblemStatus; page: number; size: number }) {
  return useQuery({
    queryKey: ['problems', 'list', filters],
    queryFn: () => unwrap(rcaApi.GET('/api/problems', { params: { query: { status: filters.status, page: filters.page, size: filters.size } } })),
    placeholderData: keepPreviousData,
  });
}

export function useProblem(id: number) {
  return useQuery({
    queryKey: ['problems', 'detail', id],
    queryFn: () => unwrap(rcaApi.GET('/api/problems/{id}', { params: { path: { id } } })),
  });
}

/** After any change the problem lists and details reload. */
function useProblemMutation<TVariables, TResult>(action: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['problems'] }),
  });
}

export const useCreateProblem = () => useProblemMutation((body: CreateProblemRequest) => unwrap(rcaApi.POST('/api/problems', { body })));

/** Edits to the form fields (impact, urgency, assignee, notes). The steps themselves are the hooks below. */
export const useUpdateProblem = () =>
  useProblemMutation((v: { id: number; body: UpdateProblemRequest }) => unwrap(rcaApi.PATCH('/api/problems/{id}', { params: { path: { id: v.id } }, body: v.body })));

// ---- the nine steps (D31): one URL per button ----

export const useAssessProblem = () =>
  useProblemMutation((v: { id: number; assignedToId?: number }) =>
    unwrap(rcaApi.POST('/api/problems/{id}/assess', { params: { path: { id: v.id } }, body: { assignedToId: v.assignedToId } })),
  );

export const useFixProblem = () =>
  useProblemMutation((v: { id: number; body: FixRequest }) => unwrap(rcaApi.POST('/api/problems/{id}/fix', { params: { path: { id: v.id } }, body: v.body })));

export const useAcceptRisk = () =>
  useProblemMutation((v: { id: number; body: AcceptRiskRequest }) => unwrap(rcaApi.POST('/api/problems/{id}/accept-risk', { params: { path: { id: v.id } }, body: v.body })));

export const useMarkDuplicate = () =>
  useProblemMutation((v: { id: number; duplicateOfId: number }) =>
    unwrap(rcaApi.POST('/api/problems/{id}/mark-duplicate', { params: { path: { id: v.id } }, body: { duplicateOfId: v.duplicateOfId } })),
  );

/** The steps that need no input: Confirm, Resolve, Complete, Cancel and Re-analyze. */
export type SimpleStep = 'CONFIRM' | 'RESOLVE' | 'COMPLETE' | 'CANCEL' | 'RE_ANALYZE';

export const useSimpleStep = () =>
  useProblemMutation((v: { id: number; step: SimpleStep }) => {
    const params = { params: { path: { id: v.id } } };
    switch (v.step) {
      case 'CONFIRM':
        return unwrap(rcaApi.POST('/api/problems/{id}/confirm', params));
      case 'RESOLVE':
        return unwrap(rcaApi.POST('/api/problems/{id}/resolve', params));
      case 'COMPLETE':
        return unwrap(rcaApi.POST('/api/problems/{id}/complete', params));
      case 'CANCEL':
        return unwrap(rcaApi.POST('/api/problems/{id}/cancel', params));
      case 'RE_ANALYZE':
        return unwrap(rcaApi.POST('/api/problems/{id}/re-analyze', params));
    }
  });

// ---- corrective actions ----

export const useAddAction = () =>
  useProblemMutation((v: { id: number; body: AddActionRequest }) => unwrap(rcaApi.POST('/api/problems/{id}/actions', { params: { path: { id: v.id } }, body: v.body })));

export const useUpdateAction = () =>
  useProblemMutation((v: { id: number; actionId: number; status: ActionStatus }) =>
    unwrap(rcaApi.PATCH('/api/problems/{id}/actions/{actionId}', { params: { path: { id: v.id, actionId: v.actionId } }, body: { status: v.status } })),
  );
