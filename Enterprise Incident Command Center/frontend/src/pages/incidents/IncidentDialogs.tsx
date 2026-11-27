import { useEffect, useState } from 'react';
import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material';
import { useCancelIncident, useHoldIncident, useReassignIncident, useReopenIncident, useResolveIncident, useUpdateIncident, type HoldReason, type Incident } from '../../api/incidents';
import { useTeams } from '../../api/lookups';
import { ErrorAlert } from '../../components/common';
import { CATEGORIES, HOLD_REASONS, HOLD_REASON_LABEL, LEVELS, RESOLUTION_CODES, SUBCATEGORIES } from './rules';

interface DialogProps {
  incident: Incident;
  open: boolean;
  onClose: () => void;
  /** Called after the server accepted the change, with a short message for the page. */
  onDone: (message: string) => void;
}

/** Resolve: the engineer who accepted the incident gives a resolution code and notes (both required, as in ResolveRequest). */
export function ResolveDialog({ incident, open, onClose, onDone }: DialogProps) {
  const resolve = useResolveIncident();
  const [code, setCode] = useState('');
  const [notes, setNotes] = useState('');
  useEffect(() => {
    if (open) {
      setCode('');
      setNotes('');
      resolve.reset();
    }
  }, [open]);

  const valid = code !== '' && notes.trim().length > 0 && notes.length <= 4000;
  const submit = () =>
    resolve.mutate(
      { id: incident.id, body: { resolutionCode: code.trim(), resolutionNotes: notes.trim() } },
      {
        onSuccess: () => {
          onDone(`${incident.number} was resolved.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Resolve {incident.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={resolve.error} />
          <TextField select label="Resolution code" value={code} onChange={(e) => setCode(e.target.value)} helperText="Choose how it was resolved." autoFocus required>
            {RESOLUTION_CODES.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Resolution notes" value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={3} helperText="What was done to fix it?" required />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={resolve.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!valid || resolve.isPending}>
          {resolve.isPending ? <CircularProgress size={22} color="inherit" /> : 'Resolve'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Reassign: choose another team. The incident goes back to NEW with nobody assigned, so that team has to accept it. */
export function ReassignDialog({ incident, open, onClose, onDone }: DialogProps) {
  const reassign = useReassignIncident();
  const teams = useTeams();
  const [teamId, setTeamId] = useState<number | ''>('');
  useEffect(() => {
    if (open) {
      setTeamId('');
      reassign.reset();
    }
  }, [open]);

  const submit = () => {
    if (teamId === '') return;
    reassign.mutate(
      { id: incident.id, teamId },
      {
        onSuccess: () => {
          onDone(`${incident.number} was reassigned. It is NEW again and the team has to accept it.`);
          onClose();
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Reassign {incident.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={reassign.error ?? teams.error} />
          <TextField select label="New team" value={teamId} onChange={(e) => setTeamId(Number(e.target.value))} helperText="The incident returns to NEW with nobody assigned.">
            {(teams.data ?? [])
              .filter((t) => t.id !== incident.assignedTeamId)
              .map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.name}
                </MenuItem>
              ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={reassign.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={teamId === '' || reassign.isPending}>
          {reassign.isPending ? <CircularProgress size={22} color="inherit" /> : 'Reassign'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Edit: only the fields that changed are sent (the backend keeps the rest). The priority is recalculated by the server. */
export function EditDialog({ incident, open, onClose, onDone }: DialogProps) {
  const update = useUpdateIncident();
  const [category, setCategory] = useState(incident.category);
  const [subcategory, setSubcategory] = useState(incident.subcategory ?? '');
  const [impact, setImpact] = useState(incident.impact);
  const [urgency, setUrgency] = useState(incident.urgency);
  const [description, setDescription] = useState(incident.description);
  useEffect(() => {
    if (open) {
      setCategory(incident.category);
      setSubcategory(incident.subcategory ?? '');
      setImpact(incident.impact);
      setUrgency(incident.urgency);
      setDescription(incident.description);
      update.reset();
    }
  }, [open]);

  const body = {
    ...(category !== incident.category ? { category } : {}),
    // An empty text clears the subcategory. When only the category changed, the server clears one that no longer fits.
    ...(subcategory !== (incident.subcategory ?? '') ? { subcategory } : {}),
    ...(impact !== incident.impact ? { impact } : {}),
    ...(urgency !== incident.urgency ? { urgency } : {}),
    ...(description.trim() !== incident.description ? { description: description.trim() } : {}),
  };
  const changed = Object.keys(body).length > 0;
  const valid = description.trim().length > 0 && description.length <= 4000;

  const submit = () =>
    update.mutate(
      { id: incident.id, body },
      {
        onSuccess: () => {
          onDone(`${incident.number} was updated.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit {incident.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={update.error} />
          <TextField
            select
            label="Category"
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              if (!(SUBCATEGORIES[e.target.value] ?? []).includes(subcategory)) setSubcategory('');
            }}
          >
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          {(SUBCATEGORIES[category] ?? []).length > 0 && (
            <TextField select label="Subcategory" value={subcategory} onChange={(e) => setSubcategory(e.target.value)}>
              <MenuItem value="">-- None --</MenuItem>
              {SUBCATEGORIES[category].map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
          )}
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
          <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={3} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!changed || !valid || update.isPending}>
          {update.isPending ? <CircularProgress size={22} color="inherit" /> : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * On hold: a reason is required. When waiting for the caller, a comment is required too, because the caller must be told
 * what is needed (the server saves it as a comment the caller can see).
 */
export function HoldDialog({ incident, open, onClose, onDone }: DialogProps) {
  const hold = useHoldIncident();
  const [reason, setReason] = useState<HoldReason | ''>('');
  const [comment, setComment] = useState('');
  useEffect(() => {
    if (open) {
      setReason('');
      setComment('');
      hold.reset();
    }
  }, [open]);

  const needsComment = reason === 'AWAITING_CALLER';
  const valid = reason !== '' && comment.length <= 4000 && (!needsComment || comment.trim().length > 0);
  const submit = () => {
    if (reason === '') return;
    hold.mutate(
      { id: incident.id, body: { reason, comment: comment.trim() || undefined } },
      {
        onSuccess: () => {
          onDone(`${incident.number} is on hold.`);
          onClose();
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Put {incident.number} on hold</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={hold.error} />
          <TextField select label="On hold reason" value={reason} onChange={(e) => setReason(e.target.value as HoldReason)} autoFocus required>
            {HOLD_REASONS.map((r) => (
              <MenuItem key={r} value={r}>
                {HOLD_REASON_LABEL[r]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label={needsComment ? 'Comment for the caller' : 'Work note (optional)'}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            multiline
            minRows={3}
            required={needsComment}
            helperText={needsComment ? 'Tell the caller what is needed. The caller can see this comment.' : 'Internal. Only the team sees it.'}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={hold.isPending}>
          Close
        </Button>
        <Button variant="contained" onClick={submit} disabled={!valid || hold.isPending}>
          {hold.isPending ? <CircularProgress size={22} color="inherit" /> : 'Put on hold'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Cancel: asks for a confirmation, because a cancelled incident is final. */
export function CancelIncidentDialog({ incident, open, onClose, onDone }: DialogProps) {
  const cancel = useCancelIncident();
  useEffect(() => {
    if (open) cancel.reset();
  }, [open]);

  const submit = () =>
    cancel.mutate(incident.id, {
      onSuccess: () => {
        onDone(`${incident.number} was cancelled.`);
        onClose();
      },
    });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Cancel {incident.number}?</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={cancel.error} />
          <span>A cancelled incident is final: it cannot be reopened or changed.</span>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={cancel.isPending}>
          Keep it
        </Button>
        <Button variant="contained" color="error" onClick={submit} disabled={cancel.isPending}>
          {cancel.isPending ? <CircularProgress size={22} color="inherit" /> : 'Cancel the incident'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Reopen: the incident goes back to work. The reason is required and is saved as a comment, so the history shows why. */
export function ReopenDialog({ incident, open, onClose, onDone }: DialogProps) {
  const reopen = useReopenIncident();
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (open) {
      setReason('');
      reopen.reset();
    }
  }, [open]);

  const valid = reason.trim().length > 0 && reason.length <= 4000;
  const submit = () =>
    reopen.mutate(
      { id: incident.id, body: { reason: reason.trim() } },
      {
        onSuccess: () => {
          onDone(`${incident.number} was reopened.`);
          onClose();
        },
      },
    );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Reopen {incident.number}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ErrorAlert error={reopen.error} />
          <TextField label="Why is it reopened?" value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={3} helperText="Saved as a comment. For example: the problem came back." autoFocus required />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={reopen.isPending}>
          Close
        </Button>
        <Button variant="contained" onClick={submit} disabled={!valid || reopen.isPending}>
          {reopen.isPending ? <CircularProgress size={22} color="inherit" /> : 'Reopen'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
