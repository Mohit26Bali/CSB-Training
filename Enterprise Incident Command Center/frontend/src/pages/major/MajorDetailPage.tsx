import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { useIncident } from '../../api/incidents';
import { useNames } from '../../api/lookups';
import { useAddTimelineEntry, useChangeMajorStatus, useMajorIncident } from '../../api/majorIncidents';
import { useAuth } from '../../auth/AuthContext';
import { ErrorAlert, MajorStatusChip, formatDateTime } from '../../components/common';
import { ActivityEntry, FormRow, RecordHeader, StatusBar } from '../../components/record';
import { PRIORITY_LABEL } from '../incidents/rules';
import { MAJOR_STATUSES, MAJOR_STATUS_LABEL, canAddUpdate, canManageMajor, nextStatus } from './rules';

/** The current time, refreshed every second while `running` (the duration clock of an open major incident). */
function useNow(running: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [running]);
  return now;
}

/** DD:HH:MM:SS. Never negative: the server's clock and the browser's may differ by a moment. */
function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(Math.floor(total / 86400))}:${p(Math.floor((total % 86400) / 3600))}:${p(Math.floor((total % 3600) / 60))}:${p(total % 60)}`;
}

/** A framed number with its caption, like the count tiles of an incident workbench. */
function Tile({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Paper sx={{ p: 2, textAlign: 'center' }}>
      <Typography variant="h4" component="div" sx={{ fontWeight: 400 }}>
        {value}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Paper>
  );
}

export function MajorDetailPage() {
  const { id } = useParams();
  const majorId = Number(id);
  const { user } = useAuth();
  const { userName } = useNames();
  const detail = useMajorIncident(majorId);
  const major = detail.data?.majorIncident;
  const incident = useIncident(major?.incidentId ?? 0);
  const addUpdate = useAddTimelineEntry();
  const changeStatus = useChangeMajorStatus();
  const [text, setText] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const open = !!major && major.status !== 'RESOLVED' && major.status !== 'CLOSED';
  const now = useNow(open);

  if (!user) return null;
  if (!Number.isInteger(majorId)) return <Alert severity="warning">This is not a valid major incident address.</Alert>;

  const next = major ? nextStatus(major.status) : null;
  // Newest first: during an outage the latest news matters most.
  const timeline = [...(detail.data?.timeline ?? [])].sort((a, b) => b.id - a.id);
  const statusChanges = timeline.filter((e) => e.entryType === 'STATUS_CHANGE').length;
  const end = major ? (major.resolvedAt ? new Date(major.resolvedAt).getTime() : now) : now;
  const duration = major ? formatDuration(end - new Date(major.declaredAt).getTime()) : '';

  const send = (event: FormEvent) => {
    event.preventDefault();
    if (!text.trim()) return;
    addUpdate.mutate({ id: majorId, text: text.trim() }, { onSuccess: () => { setText(''); setNotice(null); } });
  };

  return (
    <Stack spacing={2}>
      {detail.isLoading && <LinearProgress />}
      <ErrorAlert error={detail.error} />

      {major && (
        <>
          <RecordHeader
            kind="Major incident"
            title={`Major incident #${major.id}`}
            back={
              <Button component={RouterLink} to="/major-incidents" variant="outlined" size="small" aria-label="Back to the list" sx={{ minWidth: 36, px: 1 }}>
                &lsaquo;
              </Button>
            }
            actions={
              <>
                <Button variant="outlined" component={RouterLink} to={`/incidents/${major.incidentId}`}>
                  View incident
                </Button>
                {canManageMajor(user) && (
                  <Button variant="outlined" component={RouterLink} to={`/problems/new?incident=${major.incidentId}&major=${major.id}`}>
                    Create a problem for this major incident
                  </Button>
                )}
                {canManageMajor(user) && next && (
                  <Button
                    variant="contained"
                    disabled={changeStatus.isPending}
                    onClick={() => changeStatus.mutate({ id: major.id, status: next }, { onSuccess: () => setNotice(`The major incident is now ${MAJOR_STATUS_LABEL[next]}.`) })}
                  >
                    {changeStatus.isPending ? <CircularProgress size={22} color="inherit" /> : `Move to ${MAJOR_STATUS_LABEL[next]}`}
                  </Button>
                )}
              </>
            }
          />
          {notice && (
            <Alert severity="success" onClose={() => setNotice(null)}>
              {notice}
            </Alert>
          )}
          <ErrorAlert error={addUpdate.error ?? changeStatus.error} />

          <Paper>
            <StatusBar steps={MAJOR_STATUSES.map((s) => ({ key: s, label: MAJOR_STATUS_LABEL[s] }))} active={MAJOR_STATUSES.indexOf(major.status)} />
            <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'flex-start', p: 3 }}>
              <Box sx={{ flex: '1 1 320px', minWidth: 0 }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.5 }} useFlexGap>
                  <Typography variant="h5" component="h2">
                    {major.summary}
                  </Typography>
                  <Box component="span" sx={{ bgcolor: 'error.main', color: '#fff', px: 1.25, py: 0.25, borderRadius: 0.5, fontWeight: 700, fontSize: 14 }}>
                    Major incident
                  </Box>
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  Declared {formatDateTime(major.declaredAt)} &bull; Commander: {userName(major.commanderId)} &bull; Declared by: {userName(major.declaredById)}
                  {incident.data ? ` • Priority: ${incident.data.incident.priority} - ${PRIORITY_LABEL[incident.data.incident.priority] ?? ''}` : ''}
                </Typography>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                  Status
                </Typography>
                <MajorStatusChip status={major.status} />
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                  Duration {open ? '' : '(stopped when resolved)'}
                </Typography>
                <Typography component="div" aria-label={`Duration ${duration}`} sx={{ fontSize: 26, fontWeight: 700, fontVariantNumeric: 'tabular-nums', lineHeight: 1.1 }}>
                  {duration}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  DD:HH:MM:SS
                </Typography>
              </Box>
            </Box>
            {canManageMajor(user) && next && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 3, pb: 2 }}>
                The status moves one step at a time and never back.
              </Typography>
            )}
            {!next && (
              <Typography color="text.secondary" sx={{ px: 3, pb: 2 }}>
                This major incident is closed.
              </Typography>
            )}
          </Paper>

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' } }}>
            <Tile label="Timeline updates" value={timeline.length - statusChanges} />
            <Tile label="Status changes" value={statusChanges} />
            <Tile label="Incident priority" value={incident.data ? `P${incident.data.incident.priority}` : '-'} />
            <Tile label="Incident state" value={incident.data ? incident.data.incident.state.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : '-'} />
          </Box>

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" component="h2" gutterBottom>
                Timeline
              </Typography>

              {canManageMajor(user) && canAddUpdate(major.status) && (
                <Box component="form" onSubmit={send} sx={{ mb: 3 }}>
                  <Stack spacing={1.5}>
                    <TextField
                      label="Add an update"
                      value={text}
                      onChange={(e) => setText(e.target.value)}
                      multiline
                      minRows={2}
                      helperText="What is happening now? Up to 4000 characters."
                      sx={{ '& .MuiOutlinedInput-root': { borderLeft: '4px solid #f2c200' } }}
                    />
                    <div>
                      <Button type="submit" variant="contained" disabled={!text.trim() || text.length > 4000 || addUpdate.isPending}>
                        {addUpdate.isPending ? <CircularProgress size={22} color="inherit" /> : 'Post update'}
                      </Button>
                    </div>
                  </Stack>
                </Box>
              )}
              {!canAddUpdate(major.status) && (
                <Typography color="text.secondary" sx={{ mb: 2 }}>
                  A closed major incident takes no more updates.
                </Typography>
              )}

              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Activities: {timeline.length}
              </Typography>
              <Stack spacing={1.5}>
                {timeline.map((entry) => (
                  <ActivityEntry
                    key={entry.id}
                    author={userName(entry.authorId)}
                    kind={entry.entryType === 'STATUS_CHANGE' ? 'system' : 'comment'}
                    label={entry.entryType === 'STATUS_CHANGE' ? 'Status change' : 'Update'}
                    time={formatDateTime(entry.createdAt)}
                  >
                    {entry.text}
                  </ActivityEntry>
                ))}
              </Stack>
            </Paper>

            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" component="h2" gutterBottom>
                Details
              </Typography>
              <Stack spacing={1.5}>
                <FormRow label="Incident" stacked>
                  <RouterLink to={`/incidents/${major.incidentId}`}>{incident.data ? `${incident.data.incident.number}: ${incident.data.incident.shortDescription}` : `Incident ${major.incidentId}`}</RouterLink>
                </FormRow>
                <FormRow label="Business impact" stacked>
                  {major.impactSummary}
                </FormRow>
                <FormRow label="Commander" stacked>
                  {userName(major.commanderId)}
                </FormRow>
                <FormRow label="Declared by" stacked>
                  {userName(major.declaredById)}
                </FormRow>
                <FormRow label="Declared" stacked>
                  {formatDateTime(major.declaredAt)}
                </FormRow>
                {major.resolvedAt && (
                  <FormRow label="Resolved" stacked>
                    {formatDateTime(major.resolvedAt)}
                  </FormRow>
                )}
                {major.closedAt && (
                  <FormRow label="Closed" stacked>
                    {formatDateTime(major.closedAt)}
                  </FormRow>
                )}
              </Stack>
            </Paper>
          </Box>
        </>
      )}
    </Stack>
  );
}
