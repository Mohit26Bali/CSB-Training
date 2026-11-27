import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, MenuItem, Stack, TextField } from '@mui/material';
import type { Incident } from '../../api/incidents';
import { useUsers } from '../../api/lookups';
import { useDeclareMajorIncident } from '../../api/majorIncidents';
import { ErrorAlert } from '../../components/common';

/**
 * Escalate a priority 1 incident to a major incident. The summary and impact summary are required (as in
 * DeclareMajorIncidentRequest); the commander is optional and defaults to the person declaring.
 */
export function DeclareDialog({ incident, open, onClose }: { incident: Incident; open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const declare = useDeclareMajorIncident();
  const users = useUsers();
  const [summary, setSummary] = useState('');
  const [impact, setImpact] = useState('');
  const [commanderId, setCommanderId] = useState<number | ''>('');

  useEffect(() => {
    if (open) {
      setSummary(incident.shortDescription);
      setImpact('');
      setCommanderId('');
      declare.reset();
    }
  }, [open]);

  const valid = summary.trim().length > 0 && summary.length <= 255 && impact.trim().length > 0 && impact.length <= 4000;
  const submit = () =>
    declare.mutate(
      { incidentId: incident.id, summary: summary.trim(), impactSummary: impact.trim(), ...(commanderId === '' ? {} : { commanderId }) },
      {
        onSuccess: (major) => {
          onClose();
          navigate(`/major-incidents/${major.id}`);
        },
      },
    );

  const commanders = (users.data ?? []).filter((u) => u.role !== 'EMPLOYEE');

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Promote {incident.number} to a major incident</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <DialogContentText>A major incident gets a commander, a status and a timeline. Only priority 1 incidents can be escalated, and only once.</DialogContentText>
          <ErrorAlert error={declare.error} />
          <TextField label="Summary" value={summary} onChange={(e) => setSummary(e.target.value)} helperText={`${summary.length} / 255`} required autoFocus />
          <TextField label="Business impact" value={impact} onChange={(e) => setImpact(e.target.value)} multiline minRows={3} helperText="Who or what is affected, and how badly?" required />
          <TextField select label="Commander" value={commanderId} onChange={(e) => setCommanderId(e.target.value === '' ? '' : Number(e.target.value))} helperText="Leave empty to become the commander yourself.">
            <MenuItem value="">Me</MenuItem>
            {commanders.map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.fullName}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={declare.isPending}>
          Cancel
        </Button>
        <Button variant="contained" color="error" onClick={submit} disabled={!valid || declare.isPending}>
          {declare.isPending ? <CircularProgress size={22} color="inherit" /> : 'Promote to major incident'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
