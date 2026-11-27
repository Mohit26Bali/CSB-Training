import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { useIncidentsByIds } from '../../api/incidents';
import { useNames, useUsers } from '../../api/lookups';
import { useMajorIncident } from '../../api/majorIncidents';
import { useAddAction, useProblem, useSimpleStep, useUpdateAction, type ProblemStep, type SimpleStep } from '../../api/problems';
import { useAuth } from '../../auth/AuthContext';
import { ActionStatusChip, ErrorAlert, MajorStatusChip, PriorityChip, ProblemStatusChip, StateChip, formatDateTime } from '../../components/common';
import { FormRow, RecordForm, RecordHeader, StatusBar } from '../../components/record';
import { LEVELS, PRIORITY_LABEL } from '../incidents/rules';
import { AcceptRiskDialog, AssessDialog, ConfirmStepDialog, DuplicateDialog, EditProblemDialog, FixDialog } from './ProblemDialogs';
import { FORWARD_STEPS, PROBLEM_STATUSES, PROBLEM_STATUS_LABEL, RESOLUTION_LABEL, STEP_LABEL, canAddAction, canEditProblem, isAdmin, stepsFor } from './rules';

/** Today as yyyy-mm-dd in the user's own time zone (the due date of an action may not be in the past). */
function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
const formatDay = (value: string) => new Date(value + 'T00:00:00').toLocaleDateString();
const levelLabel = (value: number) => LEVELS.find((l) => l.value === value)?.label ?? String(value);

/** The steps that open a dialog. The others are one click. */
type DialogName = 'assess' | 'fix' | 'accept-risk' | 'duplicate' | 'cancel' | 're-analyze' | 'edit' | null;
const DIALOG_FOR: Partial<Record<ProblemStep, Exclude<DialogName, null>>> = {
  ASSESS: 'assess',
  FIX: 'fix',
  ACCEPT_RISK: 'accept-risk',
  MARK_DUPLICATE: 'duplicate',
  CANCEL: 'cancel',
  RE_ANALYZE: 're-analyze',
};
const ONE_CLICK: Partial<Record<ProblemStep, { step: SimpleStep; notice: string }>> = {
  CONFIRM: { step: 'CONFIRM', notice: 'The problem is now in root cause analysis.' },
  RESOLVE: { step: 'RESOLVE', notice: 'The problem was resolved.' },
  COMPLETE: { step: 'COMPLETE', notice: 'The problem was closed.' },
};

export function ProblemDetailPage() {
  const { id } = useParams();
  const problemId = Number(id);
  const { user } = useAuth();
  const location = useLocation();
  const { userName } = useNames();
  const users = useUsers();
  const detail = useProblem(problemId);
  const problem = detail.data?.problem;
  const actions = detail.data?.actions ?? [];
  const incidentIds = detail.data?.incidentIds ?? [];
  const major = useMajorIncident(problem?.majorIncidentId ?? 0);
  const linked = useIncidentsByIds(incidentIds);

  const addAction = useAddAction();
  const updateAction = useUpdateAction();
  const step = useSimpleStep();

  const [tab, setTab] = useState<'analysis' | 'actions'>('analysis');
  const [dialog, setDialog] = useState<DialogName>(null);
  const [notice, setNotice] = useState<string | null>((location.state as { created?: boolean } | null)?.created ? 'The problem was created.' : null);
  const [actionText, setActionText] = useState('');
  const [ownerId, setOwnerId] = useState<number | ''>('');
  const [dueDate, setDueDate] = useState('');

  if (!user) return null;
  if (!Number.isInteger(problemId)) return <Alert severity="warning">This is not a valid problem address.</Alert>;

  const actionValid = actionText.trim().length > 0 && actionText.length <= 4000 && ownerId !== '' && dueDate >= today();
  const owners = (users.data ?? []).filter((u) => u.role !== 'EMPLOYEE');
  const actionError = addAction.error ?? updateAction.error ?? step.error;

  const press = (name: ProblemStep) => {
    if (!problem) return;
    const dialogName = DIALOG_FOR[name];
    if (dialogName) {
      setDialog(dialogName);
      return;
    }
    const oneClick = ONE_CLICK[name];
    if (oneClick) step.mutate({ id: problem.id, step: oneClick.step }, { onSuccess: () => setNotice(oneClick.notice) });
  };

  const submitAction = (event: FormEvent) => {
    event.preventDefault();
    if (!problem || ownerId === '' || !actionValid) return;
    addAction.mutate(
      { id: problem.id, body: { description: actionText.trim(), ownerId, dueDate } },
      {
        onSuccess: () => {
          setActionText('');
          setOwnerId('');
          setDueDate('');
          setNotice('The corrective action was added.');
        },
      },
    );
  };

  const steps = problem ? stepsFor(user, problem) : [];
  const done = (message: string) => setNotice(message);

  return (
    <Stack spacing={2}>
      {detail.isLoading && <LinearProgress />}
      <ErrorAlert error={detail.error} />

      {problem && (
        <>
          <RecordHeader
            kind="Problem"
            title={problem.number}
            back={
              <Button component={RouterLink} to="/problems" variant="outlined" size="small" aria-label="Back to the list" sx={{ minWidth: 36, px: 1 }}>
                &lsaquo;
              </Button>
            }
            actions={
              <>
                {steps.map((name) => (
                  <Button key={name} variant={FORWARD_STEPS.includes(name) ? 'contained' : 'outlined'} color={name === 'CANCEL' ? 'error' : 'primary'} disabled={step.isPending} onClick={() => press(name)}>
                    {step.isPending && step.variables?.step === ONE_CLICK[name]?.step ? <CircularProgress size={22} color="inherit" /> : STEP_LABEL[name]}
                  </Button>
                ))}
                {canEditProblem(user, problem) && (
                  <Button variant="outlined" onClick={() => setDialog('edit')}>
                    Edit
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
            <StatusBar steps={PROBLEM_STATUSES.map((s) => ({ key: s, label: PROBLEM_STATUS_LABEL[s] }))} active={PROBLEM_STATUSES.indexOf(problem.status)} />
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', px: 3, pt: 2 }} useFlexGap>
              <ProblemStatusChip status={problem.status} />
              <PriorityChip priority={problem.priority} />
              {problem.resolutionCode && <Chip size="small" variant="outlined" label={RESOLUTION_LABEL[problem.resolutionCode]} />}
              {problem.reopenCount > 0 && <Chip size="small" variant="outlined" label={`Reopened ${problem.reopenCount}x`} />}
            </Stack>
            <RecordForm>
              <FormRow label="Number">{problem.number}</FormRow>
              <FormRow label="State">{PROBLEM_STATUS_LABEL[problem.status]}</FormRow>
              <FormRow label="Impact">{levelLabel(problem.impact)}</FormRow>
              <FormRow label="Urgency">{levelLabel(problem.urgency)}</FormRow>
              <FormRow label="Priority">{`${problem.priority} - ${PRIORITY_LABEL[problem.priority] ?? ''}`}</FormRow>
              <FormRow label="Assigned to">{problem.assignedToId == null ? 'Nobody yet' : userName(problem.assignedToId)}</FormRow>
              <FormRow label="Created by">{userName(problem.createdById)}</FormRow>
              <FormRow label="Created">{formatDateTime(problem.createdAt)}</FormRow>
              {problem.resolutionCode && <FormRow label="Resolution code">{RESOLUTION_LABEL[problem.resolutionCode]}</FormRow>}
              {problem.duplicateOfId != null && (
                <FormRow label="Duplicate of">
                  <RouterLink to={`/problems/${problem.duplicateOfId}`}>Problem #{problem.duplicateOfId}</RouterLink>
                </FormRow>
              )}
              {problem.resolvedAt && <FormRow label="Resolved">{formatDateTime(problem.resolvedAt)}</FormRow>}
              {problem.closedAt && <FormRow label="Closed">{formatDateTime(problem.closedAt)}</FormRow>}
              <FormRow label="Major incident">
                {problem.majorIncidentId == null ? (
                  'None'
                ) : (
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <RouterLink to={`/major-incidents/${problem.majorIncidentId}`}>#{problem.majorIncidentId}</RouterLink>
                    {major.data && <MajorStatusChip status={major.data.majorIncident.status} />}
                  </Stack>
                )}
              </FormRow>
              <FormRow label="Title" wide>
                {problem.title}
              </FormRow>
              <FormRow label="Description" wide>
                {problem.description}
              </FormRow>
              <FormRow label="Incidents of this problem" wide>
                <Stack spacing={0.5}>
                  {linked.incidents.map((i) => (
                    <Stack key={i.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <RouterLink to={`/incidents/${i.id}`}>{i.number}</RouterLink>
                      <span>{i.shortDescription}</span>
                      <StateChip state={i.state} />
                    </Stack>
                  ))}
                  {linked.loading && <Typography color="text.secondary">Loading...</Typography>}
                </Stack>
              </FormRow>
            </RecordForm>
            {!isAdmin(user) && problem.status !== 'CLOSED' && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 3, pb: 3 }}>
                Only an administrator moves a problem, edits it and manages the actions.
              </Typography>
            )}
          </Paper>

          <Paper>
            <Tabs value={tab} onChange={(_, v: 'analysis' | 'actions') => setTab(v)} aria-label="Record sections" sx={{ borderBottom: 1, borderColor: 'divider', minHeight: 40 }}>
              <Tab value="analysis" label="Analysis information" />
              <Tab value="actions" label={`Corrective actions (${actions.length})`} />
            </Tabs>
            {tab === 'analysis' && (
              <Box sx={{ p: 3 }}>
                <Typography variant="h6" component="h2" gutterBottom>
                  Root cause (RCA)
                </Typography>
                {problem.rootCause || problem.fixNotes || problem.workaround ? (
                  <Box sx={{ display: 'grid', gap: 1.5 }}>
                    <FormRow label="Cause notes" wide>
                      {problem.rootCause ?? '-'}
                    </FormRow>
                    <FormRow label="Category" wide>
                      {problem.rootCauseCategory ?? '-'}
                    </FormRow>
                    <FormRow label="Workaround" wide>
                      {problem.workaround ?? '-'}
                      {problem.workaroundApplied && <Chip size="small" color="success" variant="outlined" label="Applied" sx={{ ml: 1 }} />}
                    </FormRow>
                    <FormRow label="Fix notes" wide>
                      {problem.fixNotes ?? '-'}
                    </FormRow>
                  </Box>
                ) : (
                  <Typography color="text.secondary">No root cause has been recorded yet. It is written when the Fix step is pressed.</Typography>
                )}
              </Box>
            )}
            {tab === 'actions' && (
              <Box sx={{ p: 3 }}>
                <Typography variant="h6" component="h2" gutterBottom>
                  Corrective actions
                </Typography>
                {actions.length === 0 ? (
                  <Typography color="text.secondary">No corrective actions yet.</Typography>
                ) : (
                  <Table size="small" aria-label="Corrective actions">
                    <TableHead>
                      <TableRow>
                        <TableCell>Action</TableCell>
                        <TableCell>Owner</TableCell>
                        <TableCell>Due</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {actions.map((a) => (
                        <TableRow key={a.id}>
                          <TableCell sx={{ maxWidth: 360 }}>{a.description}</TableCell>
                          <TableCell>{userName(a.ownerId)}</TableCell>
                          <TableCell>{formatDay(a.dueDate)}</TableCell>
                          <TableCell>
                            <ActionStatusChip status={a.status} />
                          </TableCell>
                          <TableCell align="right">
                            {isAdmin(user) && a.status === 'OPEN' && problem.status !== 'CLOSED' && (
                              <Button size="small" disabled={updateAction.isPending} onClick={() => updateAction.mutate({ id: problem.id, actionId: a.id, status: 'DONE' }, { onSuccess: () => setNotice('The action is marked Done.') })}>
                                Mark done
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
                {problem.status !== 'CLOSED' && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    Open actions do not block the problem. When it is closed, they are marked Done automatically.
                  </Typography>
                )}

                {canAddAction(user, problem) && (
                  <Box component="form" onSubmit={submitAction} sx={{ mt: 3 }}>
                    <Typography variant="subtitle1" gutterBottom>
                      Add a corrective action
                    </Typography>
                    <Stack spacing={2}>
                      <TextField label="What must be done" value={actionText} onChange={(e) => setActionText(e.target.value)} multiline minRows={2} helperText="Up to 4000 characters." />
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                        <TextField select label="Owner" value={ownerId} onChange={(e) => setOwnerId(Number(e.target.value))} fullWidth>
                          {owners.map((u) => (
                            <MenuItem key={u.id} value={u.id}>
                              {u.fullName}
                            </MenuItem>
                          ))}
                        </TextField>
                        <TextField
                          label="Due date"
                          type="date"
                          value={dueDate}
                          onChange={(e) => setDueDate(e.target.value)}
                          slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: today() } }}
                          helperText="Today or later."
                          fullWidth
                        />
                      </Stack>
                      <div>
                        <Button type="submit" variant="contained" disabled={!actionValid || addAction.isPending}>
                          {addAction.isPending ? <CircularProgress size={22} color="inherit" /> : 'Add action'}
                        </Button>
                      </div>
                    </Stack>
                  </Box>
                )}
              </Box>
            )}
          </Paper>

          <AssessDialog problem={problem} open={dialog === 'assess'} onClose={() => setDialog(null)} onDone={done} />
          <FixDialog problem={problem} open={dialog === 'fix'} onClose={() => setDialog(null)} onDone={done} />
          <AcceptRiskDialog problem={problem} open={dialog === 'accept-risk'} onClose={() => setDialog(null)} onDone={done} />
          <DuplicateDialog problem={problem} open={dialog === 'duplicate'} onClose={() => setDialog(null)} onDone={done} />
          <ConfirmStepDialog problem={problem} step="CANCEL" open={dialog === 'cancel'} onClose={() => setDialog(null)} onDone={done} />
          <ConfirmStepDialog problem={problem} step="RE_ANALYZE" open={dialog === 're-analyze'} onClose={() => setDialog(null)} onDone={done} />
          <EditProblemDialog problem={problem} open={dialog === 'edit'} onClose={() => setDialog(null)} onDone={done} />
        </>
      )}
    </Stack>
  );
}
