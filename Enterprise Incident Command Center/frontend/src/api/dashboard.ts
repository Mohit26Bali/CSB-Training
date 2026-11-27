/**
 * The admin dashboard: the summary cards and the daily trend. Both refresh every 30 seconds (ARCHITECTURE).
 * The Dashboard service answers 200 even when one part is missing (that part is null and named in "unavailable"),
 * and answers 503 for the trend when the Incident service is down.
 */
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { dashboardApi, unwrap } from './client';
import type { components } from './generated/dashboard';

export type DashboardSummary = components['schemas']['DashboardSummaryResponse'];
export type Trends = components['schemas']['TrendsResponse'];

const REFRESH_MS = 30_000;

export function useDashboardSummary() {
  return useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: () => unwrap(dashboardApi.GET('/api/dashboard/summary')),
    refetchInterval: REFRESH_MS,
  });
}

export function useTrends(days: 7 | 30) {
  return useQuery({
    queryKey: ['dashboard', 'trends', days],
    queryFn: () => unwrap(dashboardApi.GET('/api/dashboard/trends', { params: { query: { days } } })),
    refetchInterval: REFRESH_MS,
    placeholderData: keepPreviousData,
  });
}
