/** The assignment rules (which team handles which category): list and change the team. Admin only. */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignmentApi, unwrap } from './client';
import type { components } from './generated/assignment';

export type Rule = components['schemas']['RuleResponse'];

export function useRules() {
  return useQuery({
    queryKey: ['rules'],
    queryFn: () => unwrap(assignmentApi.GET('/api/assignment-rules')),
  });
}

export function useUpdateRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: number; teamId: number }) =>
      unwrap(assignmentApi.PUT('/api/assignment-rules/{id}', { params: { path: { id: v.id } }, body: { teamId: v.teamId } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rules'] }),
  });
}
