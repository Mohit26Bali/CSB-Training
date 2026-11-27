/**
 * Everything the screens need from the Incident service: lists, one incident with its comments, and the actions.
 * The types come from the generated contract; a change in a backend DTO breaks the build here first.
 * A change (accept, resolve, ...) refreshes every incident list and detail through the 'incidents' query key.
 */
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { incidentApi, unwrap } from './client';
import type { components } from './generated/incident';

type Schemas = components['schemas'];
export type Incident = Schemas['IncidentResponse'];
export type IncidentComment = Schemas['CommentResponse'];
export type IncidentDetail = Schemas['IncidentDetailResponse'];
export type IncidentState = Incident['state'];
export type CommentType = Schemas['AddCommentRequest']['type'];
export type CreateIncidentRequest = Schemas['CreateIncidentRequest'];
export type UpdateIncidentRequest = Schemas['UpdateIncidentRequest'];
export type ResolveRequest = Schemas['ResolveRequest'];
export type ReopenRequest = Schemas['ReopenRequest'];
export type HoldRequest = Schemas['HoldRequest'];
export type HoldReason = HoldRequest['reason'];

/** mine: I reported it. assigned: my queue (assigned to me, or unassigned in my team). all: engineers and admins only. */
export type IncidentScope = 'mine' | 'assigned' | 'all';

export interface IncidentFilters {
  scope: IncidentScope;
  state?: IncidentState;
  q?: string;
  page: number;
  size: number;
}

export function useIncidents(filters: IncidentFilters) {
  return useQuery({
    queryKey: ['incidents', 'list', filters],
    queryFn: () =>
      unwrap(
        incidentApi.GET('/api/incidents', {
          params: { query: { scope: filters.scope, state: filters.state, q: filters.q || undefined, page: filters.page, size: filters.size } },
        }),
      ),
    placeholderData: keepPreviousData, // keep the old page on screen while the next one loads
  });
}

export function useIncident(id: number) {
  return useQuery({
    queryKey: ['incidents', 'detail', id],
    queryFn: () => unwrap(incidentApi.GET('/api/incidents/{id}', { params: { path: { id } } })),
    enabled: id > 0, // a page that does not know the id yet passes 0: then nothing is requested
  });
}

/** Several incidents by id (for example the incidents of a problem). Shares the cache of useIncident. */
export function useIncidentsByIds(ids: number[]) {
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: ['incidents', 'detail', id],
      queryFn: () => unwrap(incidentApi.GET('/api/incidents/{id}', { params: { path: { id } } })),
    })),
  });
  return {
    loading: results.some((r) => r.isLoading),
    error: results.find((r) => r.error)?.error ?? null,
    incidents: results.flatMap((r) => (r.data ? [r.data.incident] : [])),
  };
}

/** One mutation hook per action. All of them refresh the incident lists and details when they succeed. */
function useIncidentMutation<TVariables, TResult>(action: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['incidents'] }),
  });
}

export const useCreateIncident = () =>
  useIncidentMutation((body: CreateIncidentRequest) => unwrap(incidentApi.POST('/api/incidents', { body })));

export const useUpdateIncident = () =>
  useIncidentMutation((v: { id: number; body: UpdateIncidentRequest }) =>
    unwrap(incidentApi.PATCH('/api/incidents/{id}', { params: { path: { id: v.id } }, body: v.body })),
  );

export const useAcceptIncident = () =>
  useIncidentMutation((id: number) => unwrap(incidentApi.POST('/api/incidents/{id}/accept', { params: { path: { id } } })));

export const useResolveIncident = () =>
  useIncidentMutation((v: { id: number; body: ResolveRequest }) =>
    unwrap(incidentApi.POST('/api/incidents/{id}/resolve', { params: { path: { id: v.id } }, body: v.body })),
  );

export const useCloseIncident = () =>
  useIncidentMutation((id: number) => unwrap(incidentApi.POST('/api/incidents/{id}/close', { params: { path: { id } } })));

export const useReassignIncident = () =>
  useIncidentMutation((v: { id: number; teamId: number }) =>
    unwrap(incidentApi.POST('/api/incidents/{id}/reassign', { params: { path: { id: v.id } }, body: { teamId: v.teamId } })),
  );

export const useAddComment = () =>
  useIncidentMutation((v: { id: number; type: CommentType; body: string }) =>
    unwrap(incidentApi.POST('/api/incidents/{id}/comments', { params: { path: { id: v.id } }, body: { type: v.type, body: v.body } })),
  );

export const useHoldIncident = () =>
  useIncidentMutation((v: { id: number; body: HoldRequest }) =>
    unwrap(incidentApi.POST('/api/incidents/{id}/hold', { params: { path: { id: v.id } }, body: v.body })),
  );

export const useResumeIncident = () =>
  useIncidentMutation((id: number) => unwrap(incidentApi.POST('/api/incidents/{id}/resume', { params: { path: { id } } })));

export const useCancelIncident = () =>
  useIncidentMutation((id: number) => unwrap(incidentApi.POST('/api/incidents/{id}/cancel', { params: { path: { id } } })));

export const useReopenIncident = () =>
  useIncidentMutation((v: { id: number; body: ReopenRequest }) =>
    unwrap(incidentApi.POST('/api/incidents/{id}/reopen', { params: { path: { id: v.id } }, body: v.body })),
  );
