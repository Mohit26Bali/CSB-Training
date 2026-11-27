import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Autocomplete, Button, CircularProgress, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { useIncident, useIncidents, type Incident } from '../../api/incidents';
import { useMajorIncident, useMajorIncidents, type MajorIncident } from '../../api/majorIncidents';
import { useCreateProblem } from '../../api/problems';
import { useUsers } from '../../api/lookups';
import { ErrorAlert } from '../../components/common';
import { LEVELS } from '../incidents/rules';

/** Limits taken from the backend request DTO (CreateProblemRequest). The server checks them again. */
const MAX_TITLE = 200;
const MAX_DESCRIPTION = 4000;

const incidentLabel = (i: Incident) => `${i.number}: ${i.shortDescription}`;
const majorLabel = (m: MajorIncident) => `#${m.id}: ${m.summary}`;

interface Errors {
  title?: string;
  description?: string;
  incidents?: string;
}

/**
 * Create a problem for one or more incidents, optionally linked to a major incident. The page can be opened with
 * ?incident=<id>&major=<id> (the major incident page does this) and then starts with those chosen.
 */
export function ProblemNewPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const create = useCreateProblem();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [chosenIncidents, setChosenIncidents] = useState<Incident[]>([]);
  const [chosenMajor, setChosenMajor] = useState<MajorIncident | null>(null);
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [impact, setImpact] = useState(3);
  const [urgency, setUrgency] = useState(3);
  const [assignedToId, setAssignedToId] = useState<number | ''>('');
  const assignees = (useUsers().data ?? []).filter((u) => u.role !== 'EMPLOYEE');

  // The options of the incident picker come from the server, searched by what the user types.
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const found = useIncidents({ scope: 'all', q, page: 0, size: 20 });
  const majors = useMajorIncidents({ page: 0, size: 100 });

  // Start with what the link asked for.
  const presetIncident = useIncident(Number(params.get('incident')) || 0);
  const presetMajor = useMajorIncident(Number(params.get('major')) || 0);
  useEffect(() => {
    const incident = presetIncident.data?.incident;
    if (incident) setChosenIncidents((current) => (current.some((c) => c.id === incident.id) ? current : [...current, incident]));
  }, [presetIncident.data]);
  useEffect(() => {
    const major = presetMajor.data?.majorIncident;
    if (major) setChosenMajor((current) => current ?? major);
  }, [presetMajor.data]);

  const incidentOptions = [...chosenIncidents, ...(found.data?.items ?? []).filter((i) => !chosenIncidents.some((c) => c.id === i.id))];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const found: Errors = {};
    if (!title.trim()) found.title = 'Enter a title.';
    else if (title.length > MAX_TITLE) found.title = `At most ${MAX_TITLE} characters.`;
    if (!description.trim()) found.description = 'Describe the problem.';
    else if (description.length > MAX_DESCRIPTION) found.description = `At most ${MAX_DESCRIPTION} characters.`;
    if (chosenIncidents.length === 0) found.incidents = 'Choose at least one incident.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    create.mutate(
      { title: title.trim(), description: description.trim(), incidentIds: chosenIncidents.map((i) => i.id), impact, urgency, ...(assignedToId !== '' ? { assignedToId } : {}), ...(chosenMajor ? { majorIncidentId: chosenMajor.id } : {}) },
      { onSuccess: (problem) => navigate(`/problems/${problem.id}`, { state: { created: true } }) },
    );
  };

  return (
    <Stack spacing={2} sx={{ maxWidth: 760 }}>
      <Typography variant="h5" component="h1">
        New problem
      </Typography>
      <Typography color="text.secondary">A problem records why one or more incidents happened. It starts as New, or straight at Assess when you choose who is assigned. The root cause is recorded when the Fix step is pressed.</Typography>
      <ErrorAlert error={create.error} />
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }} noValidate>
        <Stack spacing={2.5}>
          <TextField
            label="Title"
            value={title}
            onChange={(e) => {
              setTitle(e.target.value);
              setErrors((c) => ({ ...c, title: undefined }));
            }}
            error={!!errors.title}
            helperText={errors.title ?? `${title.length} / ${MAX_TITLE}`}
            required
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
              setErrors((c) => ({ ...c, description: undefined }));
            }}
            error={!!errors.description}
            helperText={errors.description ?? 'What went wrong, and what do you want to find out?'}
            multiline
            minRows={3}
            required
          />
          <Autocomplete
            multiple
            options={incidentOptions}
            value={chosenIncidents}
            filterOptions={(options) => options}
            getOptionLabel={incidentLabel}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            onInputChange={(_, value, reason) => reason === 'input' && setSearch(value)}
            onChange={(_, value) => {
              setChosenIncidents(value);
              setErrors((c) => ({ ...c, incidents: undefined }));
            }}
            loading={found.isFetching}
            noOptionsText="No incident found"
            renderInput={(inputParams) => (
              <TextField {...inputParams} label="Incidents" error={!!errors.incidents} helperText={errors.incidents ?? 'Type a number or a word to search. At least one is needed.'} required={chosenIncidents.length === 0} />
            )}
          />
          <Autocomplete
            options={majors.data?.items ?? []}
            value={chosenMajor}
            getOptionLabel={majorLabel}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            onChange={(_, value) => setChosenMajor(value)}
            noOptionsText="No major incident"
            renderInput={(inputParams) => <TextField {...inputParams} label="Major incident (optional)" helperText="Optional: the major incident this problem came from." />}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
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
          <TextField select label="Assigned to (optional)" value={assignedToId} onChange={(e) => setAssignedToId(e.target.value === '' ? '' : Number(e.target.value))} helperText="With an assignee the problem starts at Assess.">
            <MenuItem value="">-- None --</MenuItem>
            {assignees.map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.fullName}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction="row" spacing={1.5}>
            <Button type="submit" variant="contained" disabled={create.isPending}>
              {create.isPending ? <CircularProgress size={22} color="inherit" /> : 'Create problem'}
            </Button>
            <Button onClick={() => navigate('/problems')} disabled={create.isPending}>
              Cancel
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
