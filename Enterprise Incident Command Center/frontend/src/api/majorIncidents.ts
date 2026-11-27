/**
 * Everything the screens need from the Major Incident service: the list, one major incident with its timeline,
 * declaring one, adding a timeline update and moving the status. Types come from the generated contract (D24).
 */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { majorIncidentApi, unwrap } from './client';
import type { components } from './generated/major-incident';

type Schemas = components['schemas'];
export type MajorIncident = Schemas['MajorIncidentResponse'];
export type MajorStatus = MajorIncident['status'];
export type TimelineEntry = Schemas['TimelineEntryResponse'];
export type DeclareRequest = Schemas['DeclareMajorIncidentRequest'];

export function useMajorIncidents(filters: { status?: MajorStatus; page: number; size: number }) {
  return useQuery({
    queryKey: ['majorIncidents', 'list', filters],
    queryFn: () => unwrap(majorIncidentApi.GET('/api/major-incidents', { params: { query: { status: filters.status, page: filters.page, size: filters.size } } })),
    placeholderData: keepPreviousData,
  });
}

export function useMajorIncident(id: number) {
  return useQuery({
    queryKey: ['majorIncidents', 'detail', id],
    queryFn: () => unwrap(majorIncidentApi.GET('/api/major-incidents/{id}', { params: { path: { id } } })),
    enabled: id > 0, // 0 means: not known yet, so nothing is requested
  });
}

/** After a change, the major incident lists and details reload. Declaring also reloads the incidents, which now carry the link. */
function useMajorMutation<TVariables, TResult>(action: (variables: TVariables) => Promise<TResult>, alsoIncidents = false) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['majorIncidents'] });
      if (alsoIncidents) await queryClient.invalidateQueries({ queryKey: ['incidents'] });
    },
  });
}

export const useDeclareMajorIncident = () =>
  useMajorMutation((body: DeclareRequest) => unwrap(majorIncidentApi.POST('/api/major-incidents', { body })), true);

export const useAddTimelineEntry = () =>
  useMajorMutation((v: { id: number; text: string }) =>
    unwrap(majorIncidentApi.POST('/api/major-incidents/{id}/timeline', { params: { path: { id: v.id } }, body: { text: v.text } })),
  );

export const useChangeMajorStatus = () =>
  useMajorMutation((v: { id: number; status: MajorStatus }) =>
    unwrap(majorIncidentApi.PATCH('/api/major-incidents/{id}/status', { params: { path: { id: v.id } }, body: { status: v.status } })),
  );
