import { useState, type ReactNode } from 'react';
import { Alert, Box, Button, LinearProgress, Paper, Stack, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useDashboardSummary, useTrends, type DashboardSummary } from '../../api/dashboard';
import { ErrorAlert, formatDateTime } from '../../components/common';
import { HeroIllustration } from '../../components/illustrations';
import { NAVY, PALETTE } from '../../theme';

/** The small round "i" next to a card title: says where the numbers come from. */
function Info({ text }: { text: string }) {
  return (
    <Tooltip title={text}>
      <Box component="span" tabIndex={0} role="button" aria-label={text} sx={{ width: 20, height: 20, borderRadius: '50%', border: 1, borderColor: 'text.secondary', color: 'text.secondary', fontSize: 12, fontWeight: 700, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'help' }}>
        i
      </Box>
    </Tooltip>
  );
}

const PART_NAME = { incidents: 'Incident service', majorIncidents: 'Major Incident service', problems: 'RCA service' };

/** One number with its caption. */
function Figure({ label, value, emphasis = false }: { label: string; value: number; emphasis?: boolean }) {
  return (
    <Box>
      <Typography variant="h3" component="div" sx={{ fontWeight: 400 }} color={emphasis && value > 0 ? 'error.main' : 'text.primary'}>
        {value}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}

/** A card for one service. When its part is missing the card says so; the other cards stay filled. */
function Card({ title, part, source, children }: { title: string; part: keyof typeof PART_NAME; source: string; children: ReactNode }) {
  return (
    <Paper sx={{ p: 2.5, borderTop: 3, borderTopColor: 'primary.main', boxShadow: '0 4px 14px rgba(48,65,75,0.06)' }} aria-label={title}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
        <Typography variant="h6" component="h2" sx={{ mr: 'auto' }}>
          {title}
        </Typography>
        <Info text={source} />
      </Stack>
      {children ?? (
        <Alert severity="warning" icon={false} data-testid={`unavailable-${part}`}>
          Unavailable: the {PART_NAME[part]} did not answer. The other cards are still up to date.
        </Alert>
      )}
    </Paper>
  );
}

/** "New: 2 · In progress: 3": the breakdown of a count map, in a fixed order, plus anything else the server sent. */
function Breakdown({ counts, order, labels }: { counts: Record<string, number>; order: string[]; labels: Record<string, string> }) {
  const keys = [...new Set([...order, ...Object.keys(counts)])].filter((k) => counts[k] !== undefined);
  if (keys.length === 0) {
    return (
      <Typography variant="caption" color="text.secondary">
        Nothing yet.
      </Typography>
    );
  }
  return (
    <Typography variant="body2" color="text.secondary">
      {keys.map((k) => `${labels[k] ?? k}: ${counts[k]}`).join('  ·  ')}
    </Typography>
  );
}

const STATE_LABEL: Record<string, string> = { NEW: 'New', IN_PROGRESS: 'In progress', ON_HOLD: 'On hold', RESOLVED: 'Resolved', CLOSED: 'Closed', CANCELLED: 'Cancelled' };
const MAJOR_LABEL: Record<string, string> = { DECLARED: 'Declared', MITIGATING: 'Mitigating', RESOLVED: 'Resolved', CLOSED: 'Closed' };
const PROBLEM_LABEL: Record<string, string> = {
  NEW: 'New',
  ASSESS: 'Assess',
  ROOT_CAUSE_ANALYSIS: 'Root cause analysis',
  FIX_IN_PROGRESS: 'Fix in progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};
const PRIORITY_NAME: Record<string, string> = { '1': 'P1', '2': 'P2', '3': 'P3', '4': 'P4', '5': 'P5' };

const figures = { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 2 };

function Cards({ summary }: { summary: DashboardSummary }) {
  const { incidents, majorIncidents, problems } = summary;
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
      <Card title="Incidents" part="incidents" source="From the Incident service. Open means New or In progress; critical means priority 1.">
        {incidents && (
          <Stack spacing={2}>
            <Box sx={figures}>
              <Figure label="Open" value={incidents.open} />
              <Figure label="Critical and open" value={incidents.criticalOpen} emphasis />
              <Figure label="Resolved today" value={incidents.resolvedToday} />
              <Figure label="Total" value={incidents.total} />
            </Box>
            <Breakdown counts={incidents.byState} order={['NEW', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED']} labels={STATE_LABEL} />
            <Breakdown counts={incidents.byPriority} order={['1', '2', '3', '4', '5']} labels={PRIORITY_NAME} />
          </Stack>
        )}
      </Card>
      <Card title="Major incidents" part="majorIncidents" source="From the Major Incident service. Active means Declared or Mitigating.">
        {majorIncidents && (
          <Stack spacing={2}>
            <Box sx={figures}>
              <Figure label="Active" value={majorIncidents.active} emphasis />
              <Figure label="Total" value={majorIncidents.total} />
            </Box>
            <Breakdown counts={majorIncidents.byStatus} order={['DECLARED', 'MITIGATING', 'RESOLVED', 'CLOSED']} labels={MAJOR_LABEL} />
          </Stack>
        )}
      </Card>
      <Card title="Problems and RCA" part="problems" source="From the RCA service. Open actions are corrective actions not yet Done.">
        {problems && (
          <Stack spacing={2}>
            <Box sx={figures}>
              <Figure label="Open problems" value={problems.open} />
              <Figure label="Open actions" value={problems.openActions} emphasis />
              <Figure label="Total" value={problems.total} />
            </Box>
            <Breakdown counts={problems.byStatus} order={['NEW', 'ASSESS', 'ROOT_CAUSE_ANALYSIS', 'FIX_IN_PROGRESS', 'RESOLVED', 'CLOSED']} labels={PROBLEM_LABEL} />
          </Stack>
        )}
      </Card>
    </Box>
  );
}

function Bar({ value, max, height, color }: { value: number; max: number; height: number; color: string }) {
  return <Box sx={{ width: '38%', maxWidth: 22, height: value === 0 ? 2 : Math.max(4, (value / max) * height), bgcolor: color, borderRadius: '2px 2px 0 0', opacity: value === 0 ? 0.25 : 1 }} />;
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
      <Box sx={{ width: 12, height: 12, bgcolor: color, borderRadius: '2px' }} />
      <Typography variant="caption">{label}</Typography>
    </Stack>
  );
}

/** Two bars per day (created, resolved) drawn with plain boxes: no chart library (FRONTEND-PLAN, F5). */
function TrendChart({ days }: { days: 7 | 30 }) {
  const trends = useTrends(days);
  const daily = trends.data?.daily ?? [];
  const max = Math.max(1, ...daily.flatMap((d) => [d.created, d.resolved]));
  const HEIGHT = 140;
  return (
    <Paper sx={{ p: 2.5 }} aria-label="Trend">
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2, flexWrap: 'wrap' }} useFlexGap>
        <Typography variant="h6" component="h2" sx={{ mr: 'auto' }}>
          Incidents per day
        </Typography>
        <Legend color="primary.main" label="Created" />
        <Legend color="success.main" label="Resolved" />
      </Stack>
      <ErrorAlert error={trends.error} />
      {trends.isFetching && <LinearProgress sx={{ mb: 1 }} />}
      {daily.length > 0 && (
        <Box role="img" aria-label={`Incidents created and resolved per day over the last ${trends.data?.days ?? days} days`} sx={{ display: 'flex', alignItems: 'flex-end', gap: days === 7 ? 2 : 0.5, height: HEIGHT + 28 }}>
          {daily.map((d) => (
            <Box key={d.date} sx={{ flex: 1, minWidth: 0, textAlign: 'center' }} title={`${d.date}: ${d.created} created, ${d.resolved} resolved`}>
              <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'center', gap: '2px', height: HEIGHT }}>
                <Bar value={d.created} max={max} height={HEIGHT} color="primary.main" />
                <Bar value={d.resolved} max={max} height={HEIGHT} color="success.main" />
              </Box>
              <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', fontSize: days === 7 ? 12 : 9 }}>
                {days === 7 ? d.date.slice(5) : d.date.slice(8)}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
      {!trends.isLoading && !trends.error && daily.every((d) => d.created === 0 && d.resolved === 0) && (
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          No incidents were created or resolved in this period.
        </Typography>
      )}
    </Paper>
  );
}

export function DashboardPage() {
  const summary = useDashboardSummary();
  const [days, setDays] = useState<7 | 30>(7);

  return (
    <Stack spacing={2}>
      <Box sx={{ px: 3, py: 3, borderRadius: 2, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3, overflow: 'hidden', background: `radial-gradient(circle at 90% 30%, rgba(124,152,179,0.5) 0%, rgba(124,152,179,0) 40%), linear-gradient(120deg, ${NAVY} 0%, ${PALETTE.blueSlate2} 100%)` }}>
        <Box>
          <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
            Dashboard
          </Typography>
          <Typography sx={{ color: PALETTE.paleSky }}>Where the incidents, major incidents and problems stand right now.</Typography>
        </Box>
        <Box sx={{ display: { xs: 'none', md: 'block' }, my: -2 }}>
          <HeroIllustration width={220} />
        </Box>
      </Box>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }} useFlexGap>
        <Typography variant="caption" color="text.secondary" data-testid="updated">
          {summary.dataUpdatedAt ? `Updated ${formatDateTime(new Date(summary.dataUpdatedAt).toISOString())}. Refreshes every 30 seconds.` : ''}
        </Typography>
        <ToggleButtonGroup exclusive size="small" value={days} aria-label="Trend period" onChange={(_, v: 7 | 30 | null) => v && setDays(v)}>
          <ToggleButton value={7}>7 days</ToggleButton>
          <ToggleButton value={30}>30 days</ToggleButton>
        </ToggleButtonGroup>
        <Button variant="outlined" onClick={() => summary.refetch()} disabled={summary.isFetching}>
          Refresh
        </Button>
      </Stack>

      {summary.isLoading && <LinearProgress />}
      <ErrorAlert error={summary.error} />
      {summary.data && summary.data.unavailable.length > 0 && (
        <Alert severity="warning">Some figures are missing because a service did not answer: {summary.data.unavailable.join(', ')}. The cards that could be filled are shown.</Alert>
      )}
      {summary.data && <Cards summary={summary.data} />}
      <TrendChart days={days} />
    </Stack>
  );
}
