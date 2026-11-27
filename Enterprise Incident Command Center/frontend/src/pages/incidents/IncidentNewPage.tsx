import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, CircularProgress, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { useCreateIncident } from '../../api/incidents';
import { ErrorAlert } from '../../components/common';
import { Breadcrumb } from '../../components/record';
import { CATEGORIES, LEVELS, PRIORITY_LABEL, SUBCATEGORIES, priorityFor } from './rules';

/** Limits taken from the backend request DTO (CreateIncidentRequest). The server checks them again. */
const MAX_SHORT = 160;
const MAX_DESCRIPTION = 4000;

interface Errors {
  shortDescription?: string;
  description?: string;
  category?: string;
}

export function IncidentNewPage() {
  const navigate = useNavigate();
  const create = useCreateIncident();
  const [shortDescription, setShortDescription] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [subcategory, setSubcategory] = useState('');
  const [impact, setImpact] = useState(3);
  const [urgency, setUrgency] = useState(3);
  const [errors, setErrors] = useState<Errors>({});

  const priority = priorityFor(impact, urgency);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const found: Errors = {};
    if (!shortDescription.trim()) found.shortDescription = 'Enter a short description.';
    else if (shortDescription.length > MAX_SHORT) found.shortDescription = `At most ${MAX_SHORT} characters.`;
    if (!description.trim()) found.description = 'Describe the problem.';
    else if (description.length > MAX_DESCRIPTION) found.description = `At most ${MAX_DESCRIPTION} characters.`;
    if (!category) found.category = 'Choose a category.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    create.mutate(
      { shortDescription: shortDescription.trim(), description: description.trim(), category, subcategory: subcategory || undefined, impact, urgency },
      { onSuccess: (incident) => navigate(`/incidents/${incident.id}`, { state: { created: true } }) },
    );
  };

  return (
    <Stack spacing={2} sx={{ maxWidth: 720 }}>
      <Breadcrumb items={[{ label: 'Home', to: '/' }, { label: 'Get help' }]} />
      <Typography variant="h5" component="h1">
        Report an incident
      </Typography>
      <ErrorAlert error={create.error} />
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }} noValidate>
        <Stack spacing={2.5}>
          <TextField
            label="Short description"
            value={shortDescription}
            onChange={(e) => {
              setShortDescription(e.target.value);
              setErrors((current) => ({ ...current, shortDescription: undefined }));
            }}
            error={!!errors.shortDescription}
            helperText={errors.shortDescription ?? `${shortDescription.length} / ${MAX_SHORT}`}
            required
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
              setErrors((current) => ({ ...current, description: undefined }));
            }}
            error={!!errors.description}
            helperText={errors.description ?? 'What happened, what is affected, since when?'}
            multiline
            minRows={4}
            required
          />
          <TextField select label="Category" value={category} onChange={(e) => {
              setCategory(e.target.value);
              setSubcategory('');
              setErrors((current) => ({ ...current, category: undefined }));
            }}
            error={!!errors.category} helperText={errors.category ?? 'The category decides which team receives it.'} required>
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          {(SUBCATEGORIES[category] ?? []).length > 0 && (
            <TextField select label="Subcategory" value={subcategory} onChange={(e) => setSubcategory(e.target.value)} helperText="Optional: narrows down what is affected.">
              <MenuItem value="">-- None --</MenuItem>
              {SUBCATEGORIES[category].map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select label="Impact" value={impact} onChange={(e) => setImpact(Number(e.target.value))} fullWidth helperText="How many people or systems are affected">
              {LEVELS.map((l) => (
                <MenuItem key={l.value} value={l.value}>
                  {l.label}
                </MenuItem>
              ))}
            </TextField>
            <TextField select label="Urgency" value={urgency} onChange={(e) => setUrgency(Number(e.target.value))} fullWidth helperText="How quickly it must be fixed">
              {LEVELS.map((l) => (
                <MenuItem key={l.value} value={l.value}>
                  {l.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          <Alert severity={priority === 1 ? 'warning' : 'info'} icon={false}>
            Priority: <strong>P{priority} {PRIORITY_LABEL[priority]}</strong> (impact + urgency - 1). The server calculates the real value.
          </Alert>
          <Stack direction="row" spacing={1.5}>
            <Button type="submit" variant="contained" disabled={create.isPending}>
              {create.isPending ? <CircularProgress size={22} color="inherit" /> : 'Report incident'}
            </Button>
            <Button onClick={() => navigate('/incidents')} disabled={create.isPending}>
              Cancel
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
