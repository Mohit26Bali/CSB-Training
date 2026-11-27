/**
 * Reference data the screens need to show names instead of ids. The backend sends ids (reporterId, assignedTeamId, ...),
 * so the user and team lists are loaded once after login and cached for the whole session.
 */
import { useQuery } from '@tanstack/react-query';
import { assignmentApi, unwrap } from './client';

export function useTeams() {
  return useQuery({
    queryKey: ['teams'],
    queryFn: () => unwrap(assignmentApi.GET('/api/teams')),
    staleTime: Infinity,
  });
}

export function useUsers() {
  return useQuery({
    queryKey: ['users'],
    queryFn: () => unwrap(assignmentApi.GET('/api/users')),
    staleTime: Infinity,
  });
}

/** Name lookups. While the lists are loading, or for an unknown id, they return a readable fallback. */
export function useNames() {
  const teams = useTeams();
  const users = useUsers();
  return {
    loading: teams.isLoading || users.isLoading,
    teamName: (id?: number | null): string => (id == null ? 'No team' : (teams.data?.find((t) => t.id === id)?.name ?? `Team ${id}`)),
    userName: (id?: number | null): string => (id == null ? 'Nobody' : (users.data?.find((u) => u.id === id)?.fullName ?? `User ${id}`)),
  };
}
