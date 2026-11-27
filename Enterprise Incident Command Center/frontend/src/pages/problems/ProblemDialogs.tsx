import { useEffect, useState } from 'react';
import { Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { useUsers } from '../../api/lookups';
import { useAcceptRisk, useAssessProblem, useFixProblem, useMarkDuplicate, useProblems, useSimpleStep, useUpdateProblem, type Problem, type SimpleStep } from '../../api/problems';
import { ErrorAlert } from '../../components/common';
import { LEVELS } from '../incidents/rules';

interface DialogProps {
  problem: Problem;
  open: boolean;
  onClose: () => void;
  /** Called after the server accepted the step, with a short message for the page. */
  onDone: (message: string) => void;
}

/** Everyone who can work on a problem (not employees). */
function useAssignees() {
  const users = useUsers();
  return (users.data ?? []).filter((u) => u.role !== 'EMPLOYEE');
}

function DialogButtons({ pending, valid, label, onClose, onSubmit }: { pending: boolean; valid: boolean; label: string; onClose: () => void; onSubmit: () => void }) {
  return (
    <DialogActions>
      <Button onClick={onClose} disabled={pending}>
        Close
      </Button>
      <Button variant="contained" onClick={onSubmit} disabled={!valid || pending}>
        {pending ? <CircularProgress size={22} color="inherit" /> : label}
      </Button>
    </DialogActions>
  );
}

/** Assess: an assignee is required (the problem already has one when it was created with an assignee). */
export function AssessDialog({ problem, open, onClose, onDone }: DialogProps) {
  const assess = useAssessProblem();
  const assignees = useAssignees();
  const [assignedToId, setAssignedToId] = useState<number | ''>('');
  useEffect(() => {
    if (open) {
      setAssignedToId(problem.assignedToId ?? '');
      assess.reset();
    }
  }, [open]);

  const submit = () =>
    assess.mutate(
      { id: problem.id, assignedToId: assignedToId === '' ? undefined : assignedToId },
      {
        onSuccess: () => {
          onDone(`${problem.number} is being assessed.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Assess {problem.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={assess.error} />
          <TextField select label="Assigned to" value={assignedToId} onChange={(e) => setAssignedToId(Number(e.target.value))} autoFocus required helperText="Who analyses this problem.">
            {assignees.map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.fullName}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogButtons pending={assess.isPending} valid={assignedToId !== ''} label="Assess" onClose={onClose} onSubmit={submit} />
    </Dialog>
  );
}

/** Fix: both the cause notes and the fix notes are required (as in ServiceNow). Anything already written is kept. */
export function FixDialog({ problem, open, onClose, onDone }: DialogProps) {
  const fix = useFixProblem();
  const [causeNotes, setCauseNotes] = useState('');
  const [fixNotes, setFixNotes] = useState('');
  const [category, setCategory] = useState('');
  const [workaround, setWorkaround] = useState('');
  useEffect(() => {
    if (open) {
      setCauseNotes(problem.rootCause ?? '');
      setFixNotes(problem.fixNotes ?? '');
      setCategory(problem.rootCauseCategory ?? '');
      setWorkaround(problem.workaround ?? '');
      fix.reset();
    }
  }, [open]);

  const valid = causeNotes.trim().length > 0 && causeNotes.length <= 4000 && fixNotes.trim().length > 0 && fixNotes.length <= 4000 && category.length <= 60 && workaround.length <= 4000;
  const submit = () =>
    fix.mutate(
      { id: problem.id, body: { causeNotes: causeNotes.trim(), fixNotes: fixNotes.trim(), rootCauseCategory: category.trim() || undefined, workaround: workaround.trim() || undefined } },
      {
        onSuccess: () => {
          onDone(`The fix for ${problem.number} is in progress.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Fix {problem.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={fix.error} />
          <TextField label="Cause notes" value={causeNotes} onChange={(e) => setCauseNotes(e.target.value)} multiline minRows={3} helperText="Why did it happen? Up to 4000 characters." autoFocus required />
          <TextField label="Fix notes" value={fixNotes} onChange={(e) => setFixNotes(e.target.value)} multiline minRows={3} helperText="What will be done to fix it?" required />
          <TextField label="Root cause category" value={category} onChange={(e) => setCategory(e.target.value)} helperText="Optional. For example Hardware, Configuration, Capacity." />
          <TextField label="Workaround" value={workaround} onChange={(e) => setWorkaround(e.target.value)} multiline minRows={2} helperText="Optional: how to get around it until it is fixed." />
        </Stack>
      </DialogContent>
      <DialogButtons pending={fix.isPending} valid={valid} label="Fix" onClose={onClose} onSubmit={submit} />
    </Dialog>
  );
}

/** Accept risk: closes the problem. If a workaround was put in place, it must be described. */
export function AcceptRiskDialog({ problem, open, onClose, onDone }: DialogProps) {
  const accept = useAcceptRisk();
  const [applied, setApplied] = useState(false);
  const [workaround, setWorkaround] = useState('');
  useEffect(() => {
    if (open) {
      setApplied(false);
      setWorkaround(problem.workaround ?? '');
      accept.reset();
    }
  }, [open]);

  const valid = workaround.length <= 4000 && (!applied || workaround.trim().length > 0);
  const submit = () =>
    accept.mutate(
      { id: problem.id, body: { workaroundApplied: applied, workaround: workaround.trim() || undefined } },
      {
        onSuccess: () => {
          onDone(`${problem.number} was closed: the risk is accepted.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Accept the risk of {problem.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={accept.error} />
          <Typography variant="body2" color="text.secondary">
            The problem will be closed without a fix. Do this only when living with it is the decision.
          </Typography>
          <FormControlLabel control={<Checkbox checked={applied} onChange={(e) => setApplied(e.target.checked)} />} label="A workaround has been applied" />
          <TextField label="Workaround" value={workaround} onChange={(e) => setWorkaround(e.target.value)} multiline minRows={2} required={applied} helperText={applied ? 'Required when a workaround has been applied.' : 'Optional.'} />
        </Stack>
      </DialogContent>
      <DialogButtons pending={accept.isPending} valid={valid} label="Accept risk" onClose={onClose} onSubmit={submit} />
    </Dialog>
  );
}

/** Mark duplicate: choose the original problem. This one is closed and its incidents are linked to the original. */
export function DuplicateDialog({ problem, open, onClose, onDone }: DialogProps) {
  const mark = useMarkDuplicate();
  const list = useProblems({ page: 0, size: 100 });
  const [originalId, setOriginalId] = useState<number | ''>('');
  useEffect(() => {
    if (open) {
      setOriginalId('');
      mark.reset();
    }
  }, [open]);

  const candidates = (list.data?.items ?? []).filter((p) => p.id !== problem.id && p.resolutionCode !== 'DUPLICATE');
  const submit = () => {
    if (originalId === '') return;
    mark.mutate(
      { id: problem.id, duplicateOfId: originalId },
      {
        onSuccess: () => {
          onDone(`${problem.number} was closed as a duplicate.`);
          onClose();
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Mark {problem.number} as a duplicate</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={mark.error} />
          <TextField select label="Duplicate of" value={originalId} onChange={(e) => setOriginalId(Number(e.target.value))} autoFocus required helperText="The problem that already covers this one.">
            {candidates.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.number} - {p.title}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogButtons pending={mark.isPending} valid={originalId !== ''} label="Mark duplicate" onClose={onClose} onSubmit={submit} />
    </Dialog>
  );
}

const SIMPLE_CONFIRM: Partial<Record<SimpleStep, { title: string; text: string; button: string; done: string }>> = {
  CANCEL: { title: 'Cancel', text: 'The problem is closed as canceled. This does not delete it.', button: 'Cancel the problem', done: 'was canceled.' },
  RE_ANALYZE: { title: 'Re-analyze', text: 'The problem goes back to root cause analysis. The cause and fix notes are kept.', button: 'Re-analyze', done: 'is back in root cause analysis.' },
};

/** Asks for a confirmation before the two steps that are hard to undo or reopen a problem. */
export function ConfirmStepDialog({ problem, step, open, onClose, onDone }: DialogProps & { step: 'CANCEL' | 'RE_ANALYZE' }) {
  const run = useSimpleStep();
  useEffect(() => {
    if (open) run.reset();
  }, [open]);
  const text = SIMPLE_CONFIRM[step]!;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        {text.title} {problem.number}?
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={run.error} />
          <Typography>{text.text}</Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={run.isPending}>
          Keep it as it is
        </Button>
        <Button
          variant="contained"
          color={step === 'CANCEL' ? 'error' : 'primary'}
          disabled={run.isPending}
          onClick={() =>
            run.mutate(
              { id: problem.id, step },
              {
                onSuccess: () => {
                  onDone(`${problem.number} ${text.done}`);
                  onClose();
                },
              },
            )
          }
        >
          {run.isPending ? <CircularProgress size={22} color="inherit" /> : text.button}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Edit: only the fields that changed are sent. The priority is recalculated by the server. */
export function EditProblemDialog({ problem, open, onClose, onDone }: DialogProps) {
  const update = useUpdateProblem();
  const assignees = useAssignees();
  const [impact, setImpact] = useState(problem.impact);
  const [urgency, setUrgency] = useState(problem.urgency);
  const [assignedToId, setAssignedToId] = useState<number | ''>(problem.assignedToId ?? '');
  const [causeNotes, setCauseNotes] = useState(problem.rootCause ?? '');
  const [fixNotes, setFixNotes] = useState(problem.fixNotes ?? '');
  const [category, setCategory] = useState(problem.rootCauseCategory ?? '');
  const [workaround, setWorkaround] = useState(problem.workaround ?? '');
  useEffect(() => {
    if (open) {
      setImpact(problem.impact);
      setUrgency(problem.urgency);
      setAssignedToId(problem.assignedToId ?? '');
      setCauseNotes(problem.rootCause ?? '');
      setFixNotes(problem.fixNotes ?? '');
      setCategory(problem.rootCauseCategory ?? '');
      setWorkaround(problem.workaround ?? '');
      update.reset();
    }
  }, [open]);

  const body = {
    ...(impact !== problem.impact ? { impact } : {}),
    ...(urgency !== problem.urgency ? { urgency } : {}),
    ...(assignedToId !== '' && assignedToId !== problem.assignedToId ? { assignedToId } : {}),
    ...(causeNotes.trim() !== (problem.rootCause ?? '') && causeNotes.trim() ? { causeNotes: causeNotes.trim() } : {}),
    ...(fixNotes.trim() !== (problem.fixNotes ?? '') && fixNotes.trim() ? { fixNotes: fixNotes.trim() } : {}),
    ...(category.trim() !== (problem.rootCauseCategory ?? '') ? { rootCauseCategory: category.trim() } : {}),
    ...(workaround.trim() !== (problem.workaround ?? '') ? { workaround: workaround.trim() } : {}),
  };
  const changed = Object.keys(body).length > 0;
  const valid = causeNotes.length <= 4000 && fixNotes.length <= 4000 && category.length <= 60 && workaround.length <= 4000;

  const submit = () =>
    update.mutate(
      { id: problem.id, body },
      {
        onSuccess: () => {
          onDone(`${problem.number} was updated.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit {problem.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={update.error} />
          <Stack direction="row" spacing={2}>
            <TextField select label="Impact" value={impact} onChange={(e) => setImpact(Number(e.target.value))} fullWidth>
              {LEVELS.map((l) => (
                <MenuItem key={l.value} value={l.value}>
                  {l.label}
                </MenuItem>
              ))}
            </TextField>
            <TextField select label="Urgency" value={urgency} onChange={(e) => setUrgency(Number(e.target.value))} fullWidth>
              {LEVELS.map((l) => (
                <MenuItem key={l.value} value={l.value}>
                  {l.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          <TextField select label="Assigned to" value={assignedToId} onChange={(e) => setAssignedToId(Number(e.target.value))}>
            {assignees.map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.fullName}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Cause notes" value={causeNotes} onChange={(e) => setCauseNotes(e.target.value)} multiline minRows={2} />
          <TextField label="Fix notes" value={fixNotes} onChange={(e) => setFixNotes(e.target.value)} multiline minRows={2} />
          <TextField label="Root cause category" value={category} onChange={(e) => setCategory(e.target.value)} />
          <TextField label="Workaround" value={workaround} onChange={(e) => setWorkaround(e.target.value)} multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogButtons pending={update.isPending} valid={changed && valid} label="Save" onClose={onClose} onSubmit={submit} />
    </Dialog>
  );
}
