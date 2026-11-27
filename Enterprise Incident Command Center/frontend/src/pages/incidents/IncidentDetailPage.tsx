import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import { Alert, Box, Button, Chip, CircularProgress, LinearProgress, MenuItem, Paper, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useAcceptIncident, useAddComment, useCloseIncident, useIncident, useResumeIncident, type CommentType } from '../../api/incidents';
import { useNames } from '../../api/lookups';
import { useAuth } from '../../auth/AuthContext';
import { ErrorAlert, MajorStatusChip, PriorityChip, StateChip, formatDateTime } from '../../components/common';
import { useMajorIncident } from '../../api/majorIncidents';
import { ActivityEntry, FormRow, RecordForm, RecordHeader } from '../../components/record';
import { DeclareDialog } from '../major/DeclareDialog';
import { canDeclare } from '../major/rules';
import { CancelIncidentDialog, EditDialog, HoldDialog, ReassignDialog, ReopenDialog, ResolveDialog } from './IncidentDialogs';
import { HOLD_REASON_LABEL, LEVELS, PRIORITY_LABEL, STATE_LABEL, canAccept, canCancel, canClose, canComment, canEdit, canHold, canReassign, canReopen, canResolve, canResume, canWriteWorkNote } from './rules';

const levelLabel = (value: number) => LEVELS.find((l) => l.value === value)?.label ?? String(value);

export function IncidentDetailPage() {
  const { id } = useParams();
  const incidentId = Number(id);
  const { user } = useAuth();
  const location = useLocation();
  const { teamName, userName } = useNames();
  const detail = useIncident(incidentId);
  const accept = useAcceptIncident();
  const close = useCloseIncident();
  const resume = useResumeIncident();
  const addComment = useAddComment();

  const [dialog, setDialog] = useState<'resolve' | 'hold' | 'cancel' | 'reopen' | 'reassign' | 'edit' | 'escalate' | null>(null);
  const [notice, setNotice] = useState<string | null>((location.state as { created?: boolean } | null)?.created ? 'Your incident was reported. The team that handles its category will pick it up.' : null);
  const [tab, setTab] = useState<'notes' | 'major'>('notes');
  const major = useMajorIncident(detail.data?.incident.majorIncidentId ?? 0);
  const [commentType, setCommentType] = useState<CommentType>('COMMENT');
  const [commentText, setCommentText] = useState('');

  if (!user) return null;
  if (!Number.isInteger(incidentId)) return <Alert severity="warning">This is not a valid incident address.</Alert>;

  const incident = detail.data?.incident;
  const comments = detail.data?.comments ?? [];
  const actionError = accept.error ?? close.error ?? resume.error ?? addComment.error;

  const send = (event: FormEvent) => {
    event.preventDefault();
    if (!commentText.trim()) return;
    addComment.mutate(
      { id: incidentId, type: commentType, body: commentText.trim() },
      {
        onSuccess: () => {
          setCommentText('');
          setNotice(null);
        },
      },
    );
  };

  return (
    <Stack spacing={2}>
      {detail.isLoading && <LinearProgress />}
      <ErrorAlert error={detail.error} />

      {incident && (
        <>
          <RecordHeader
            kind="Incident"
            title={incident.number}
            back={
              <Button component={RouterLink} to="/incidents" variant="outlined" size="small" aria-label="Back to the list" sx={{ minWidth: 36, px: 1 }}>
                &lsaquo;
              </Button>
            }
            actions={
              <>
                {canAccept(user, incident) && (
                  <Button variant="contained" disabled={accept.isPending} onClick={() => accept.mutate(incident.id, { onSuccess: () => setNotice(`You accepted ${incident.number}.`) })}>
                    {accept.isPending ? <CircularProgress size={22} color="inherit" /> : 'Accept'}
                  </Button>
                )}
                {canResolve(user, incident) && (
                  <Button variant="contained" color="success" onClick={() => setDialog('resolve')}>
                    Resolve
                  </Button>
                )}
                {canHold(user, incident) && (
                  <Button variant="outlined" onClick={() => setDialog('hold')}>
                    Put on hold
                  </Button>
                )}
                {canResume(user, incident) && (
                  <Button variant="contained" disabled={resume.isPending} onClick={() => resume.mutate(incident.id, { onSuccess: () => setNotice(`${incident.number} is back in progress.`) })}>
                    {resume.isPending ? <CircularProgress size={22} color="inherit" /> : 'Resume'}
                  </Button>
                )}
                {canReopen(user, incident) && (
                  <Button variant="contained" color="warning" onClick={() => setDialog('reopen')}>
                    Reopen
                  </Button>
                )}
                {canClose(user, incident) && (
                  <Button variant="contained" disabled={close.isPending} onClick={() => close.mutate(incident.id, { onSuccess: () => setNotice(`${incident.number} was closed.`) })}>
                    {close.isPending ? <CircularProgress size={22} color="inherit" /> : 'Close'}
                  </Button>
                )}
                {canReassign(user, incident) && (
                  <Button variant="outlined" onClick={() => setDialog('reassign')}>
                    Reassign
                  </Button>
                )}
                {canCancel(user, incident) && (
                  <Button variant="outlined" color="error" onClick={() => setDialog('cancel')}>
                    Cancel incident
                  </Button>
                )}
                {canEdit(user, incident) && (
                  <Button variant="outlined" onClick={() => setDialog('edit')}>
                    Edit
                  </Button>
                )}
                {incident.majorIncidentId != null && (
                  <Button variant="outlined" color="error" component={RouterLink} to={`/major-incidents/${incident.majorIncidentId}`}>
                    View major incident
                  </Button>
                )}
                {canDeclare(user, incident) && (
                  <Button variant="outlined" color="error" onClick={() => setDialog('escalate')}>
                    Escalate to major incident
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
          <ErrorAlert error={actionError} />

          <Paper>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', px: 3, pt: 2 }} useFlexGap>
              <StateChip state={incident.state} />
              <PriorityChip priority={incident.priority} />
              {incident.majorIncidentId != null && (
                <Chip size="small" color="error" variant="outlined" clickable component={RouterLink} to={`/major-incidents/${incident.majorIncidentId}`} label={`Major incident #${incident.majorIncidentId}`} />
              )}
            </Stack>
            <RecordForm>
              <FormRow label="Number">{incident.number}</FormRow>
              <FormRow label="State">{STATE_LABEL[incident.state]}</FormRow>
              {incident.holdReason && <FormRow label="On hold reason">{HOLD_REASON_LABEL[incident.holdReason]}</FormRow>}
              <FormRow label="Category">{incident.category}</FormRow>
              <FormRow label="Subcategory">{incident.subcategory ?? '-'}</FormRow>
              <FormRow label="Impact">{levelLabel(incident.impact)}</FormRow>
              <FormRow label="Reported by">{userName(incident.reporterId)}</FormRow>
              <FormRow label="Urgency">{levelLabel(incident.urgency)}</FormRow>
              <FormRow label="Team">{teamName(incident.assignedTeamId)}</FormRow>
              <FormRow label="Priority">{`${incident.priority} - ${PRIORITY_LABEL[incident.priority] ?? ''}`}</FormRow>
              <FormRow label="Assigned to">{incident.assignedToId == null ? 'Nobody yet' : userName(incident.assignedToId)}</FormRow>
              <FormRow label="Created">{formatDateTime(incident.createdAt)}</FormRow>
              <FormRow label="Last updated">{formatDateTime(incident.updatedAt)}</FormRow>
              {incident.resolvedAt && <FormRow label="Resolved">{formatDateTime(incident.resolvedAt)}</FormRow>}
              {incident.closedAt && <FormRow label="Closed">{formatDateTime(incident.closedAt)}</FormRow>}
              <FormRow label="Short description" wide>
                {incident.shortDescription}
              </FormRow>
              <FormRow label="Description" wide>
                {incident.description}
              </FormRow>
            </RecordForm>

            {incident.resolutionCode && (incident.state === 'RESOLVED' || incident.state === 'CLOSED') && (
              <Box sx={{ px: 3, pb: 3 }}>
                <Alert severity="success" icon={false}>
                  <strong>Resolution: {incident.resolutionCode}</strong>
                  <br />
                  {incident.resolutionNotes}
                </Alert>
              </Box>
            )}
          </Paper>

          <Paper>
            <Tabs value={incident.majorIncidentId == null ? 'notes' : tab} onChange={(_, v: 'notes' | 'major') => setTab(v)} aria-label="Record sections" sx={{ borderBottom: 1, borderColor: 'divider', minHeight: 40 }}>
              <Tab value="notes" label="Notes" />
              {incident.majorIncidentId != null && <Tab value="major" label="Major incident" />}
            </Tabs>
            {incident.majorIncidentId != null && tab === 'major' && (
              <Box sx={{ p: 3 }}>
                <RecordForm>
                  <FormRow label="Major incident">
                    <RouterLink to={`/major-incidents/${incident.majorIncidentId}`}>{`Major incident #${incident.majorIncidentId}`}</RouterLink>
                  </FormRow>
                  <FormRow label="Status">{major.data ? <MajorStatusChip status={major.data.majorIncident.status} /> : 'Loading...'}</FormRow>
                  <FormRow label="Commander">{major.data ? userName(major.data.majorIncident.commanderId) : '...'}</FormRow>
                  <FormRow label="Declared">{major.data ? formatDateTime(major.data.majorIncident.declaredAt) : '...'}</FormRow>
                  <FormRow label="Summary" wide>
                    {major.data ? major.data.majorIncident.summary : '...'}
                  </FormRow>
                </RecordForm>
              </Box>
            )}
            {(incident.majorIncidentId == null || tab === 'notes') && (
              <Box sx={{ p: 3 }}>
                <Typography variant="h6" component="h2" gutterBottom>
                  Comments
                </Typography>
                <Stack spacing={1.5}>
                  {comments.length === 0 && <Typography color="text.secondary">No comments yet.</Typography>}
                  {comments.map((c) => (
                    <ActivityEntry
                      key={c.id}
                      author={userName(c.authorId)}
                      kind={c.type === 'WORK_NOTE' ? 'note' : 'comment'}
                      label={c.type === 'WORK_NOTE' ? 'Work notes' : 'Comments'}
                      time={formatDateTime(c.createdAt)}
                      extra={c.type === 'WORK_NOTE' ? <Chip size="small" color="warning" variant="outlined" label="Work note (internal)" /> : undefined}
                    >
                      {c.body}
                    </ActivityEntry>
                  ))}
                </Stack>

            {incident.state === 'CLOSED' && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                This incident is closed. It can no longer be changed. If the problem is back, report a new incident.
              </Typography>
            )}
            {canComment(incident) ? (
              <Box component="form" onSubmit={send} sx={{ mt: 3 }}>
                <Stack spacing={1.5}>
                  {canWriteWorkNote(user) && (
                    <TextField select size="small" label="Type" value={commentType} onChange={(e) => setCommentType(e.target.value as CommentType)} sx={{ maxWidth: 260 }}>
                      <MenuItem value="COMMENT">Comment (the reporter sees it)</MenuItem>
                      <MenuItem value="WORK_NOTE">Work note (internal)</MenuItem>
                    </TextField>
                  )}
                  <TextField label="Add a comment" value={commentText} onChange={(e) => setCommentText(e.target.value)} multiline minRows={2} />
                  <div>
                    <Button type="submit" variant="contained" disabled={!commentText.trim() || addComment.isPending}>
                      {addComment.isPending ? <CircularProgress size={22} color="inherit" /> : 'Add'}
                    </Button>
                  </div>
                </Stack>
              </Box>
            ) : (
              <Typography color="text.secondary" sx={{ mt: 2 }}>
                A closed or cancelled incident takes no new comments.
              </Typography>
            )}
              </Box>
            )}
          </Paper>

          <ResolveDialog incident={incident} open={dialog === 'resolve'} onClose={() => setDialog(null)} onDone={setNotice} />
          <ReopenDialog incident={incident} open={dialog === 'reopen'} onClose={() => setDialog(null)} onDone={setNotice} />
          <HoldDialog incident={incident} open={dialog === 'hold'} onClose={() => setDialog(null)} onDone={setNotice} />
          <CancelIncidentDialog incident={incident} open={dialog === 'cancel'} onClose={() => setDialog(null)} onDone={setNotice} />
          <ReassignDialog incident={incident} open={dialog === 'reassign'} onClose={() => setDialog(null)} onDone={setNotice} />
          <EditDialog incident={incident} open={dialog === 'edit'} onClose={() => setDialog(null)} onDone={setNotice} />
          <DeclareDialog incident={incident} open={dialog === 'escalate'} onClose={() => setDialog(null)} />
        </>
      )}
    </Stack>
  );
}
