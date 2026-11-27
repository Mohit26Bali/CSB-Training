import { useEffect, useState } from 'react';
import { EmptyState } from '../../components/illustrations';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { useIncidents, type IncidentScope, type IncidentState } from '../../api/incidents';
import { useNames } from '../../api/lookups';
import { useAuth } from '../../auth/AuthContext';
import { ErrorAlert, PriorityDot, formatDateTime, timeAgo } from '../../components/common';
import { Breadcrumb } from '../../components/record';
import { PRIORITY_LABEL } from './rules';
import { STRIP } from '../../theme';
import { LIST_TITLE, SCOPES, SCOPE_LABEL, STATES, STATE_LABEL } from './rules';

/**
 * The incident list, the same screen for every role. What differs is decided by the role:
 * the title, the scopes offered (an employee only sees their own incidents) and the default scope.
 */
export function IncidentListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { teamName, userName } = useNames();

  const scopes = user ? SCOPES[user.role] : [];
  const [scope, setScope] = useState<IncidentScope>(scopes[0] ?? 'mine');
  const [state, setState] = useState<IncidentState | ''>('');
  const [params] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const [q, setQ] = useState((params.get('q') ?? '').trim());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  // Wait until the user stops typing before asking the server.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  const incidents = useIncidents({ scope, state: state || undefined, q, page, size });
  if (!user) return null;
  const rows = incidents.data?.items ?? [];
  const employee = user.role === 'EMPLOYEE';

  // The employee sees a simple list of their own requests: what, state, and how long ago it changed.
  if (employee) {
    return (
      <Stack spacing={2}>
        <Breadcrumb items={[{ label: 'Home', to: '/' }, { label: 'Requests' }]} />
        <Paper>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', p: 2, borderBottom: 1, borderColor: 'divider' }} useFlexGap>
            <Typography variant="h5" component="h1">
              {LIST_TITLE[user.role]}
            </Typography>
            <Button variant="contained" component={RouterLink} to="/incidents/new">
              Report an incident
            </Button>
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ p: 2, justifyContent: 'flex-end' }}>
            <TextField
              select
              size="small"
              label="State"
              value={state}
              onChange={(e) => {
                setState(e.target.value as IncidentState | '');
                setPage(0);
              }}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">All states</MenuItem>
              {STATES.map((s) => (
                <MenuItem key={s} value={s}>
                  {STATE_LABEL[s]}
                </MenuItem>
              ))}
            </TextField>
            <TextField size="small" label="Search number or text" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: { md: 320 } }} />
          </Stack>
          <ErrorAlert error={incidents.error} />
          {incidents.isFetching && <LinearProgress />}
          <TableContainer>
            <Table size="small" aria-label="Incidents">
              <TableHead>
                <TableRow>
                  <TableCell>Incident</TableCell>
                  <TableCell>State</TableCell>
                  <TableCell>Updated</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((incident) => (
                  <TableRow key={incident.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/incidents/${incident.id}`)}>
                    <TableCell>
                      <RouterLink to={`/incidents/${incident.id}`} onClick={(e) => e.stopPropagation()}>
                        {incident.shortDescription}
                      </RouterLink>
                      <Typography variant="caption" color="text.secondary" component="div">
                        {incident.number}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" component="div">
                        {incident.priority} - {PRIORITY_LABEL[incident.priority] ?? ''}
                      </Typography>
                    </TableCell>
                    <TableCell>{STATE_LABEL[incident.state]}</TableCell>
                    <TableCell title={formatDateTime(incident.updatedAt)}>{timeAgo(incident.updatedAt)}</TableCell>
                  </TableRow>
                ))}
                {!incidents.isLoading && rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3}>
                      <EmptyState message="No incidents match." />
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={incidents.data?.total ?? 0}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50]}
            labelDisplayedRows={({ from, to, count }) => `${from} to ${to} of ${count}`}
            onPageChange={(_, next) => setPage(next)}
            onRowsPerPageChange={(e) => {
              setSize(Number(e.target.value));
              setPage(0);
            }}
          />
        </Paper>
      </Stack>
    );
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }} useFlexGap>
        <Typography variant="h5" component="h1">
          {LIST_TITLE[user.role]}
        </Typography>
        {user.role === 'EMPLOYEE' && (
          <Button variant="contained" component={RouterLink} to="/incidents/new">
            Report an incident
          </Button>
        )}
      </Stack>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, bgcolor: STRIP, px: 2, py: 1, border: 1, borderColor: 'divider', borderRadius: 1 }}>
        {scopes.length > 1 && (
          <ToggleButtonGroup
            exclusive
            size="small"
            value={scope}
            aria-label="Which incidents"
            onChange={(_, value: IncidentScope | null) => {
              if (value) {
                setScope(value);
                setPage(0);
              }
            }}
          >
            {scopes.map((s) => (
              <ToggleButton key={s} value={s}>
                {SCOPE_LABEL[s]}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        )}
        <TextField
          select
          size="small"
          label="State"
          value={state}
          onChange={(e) => {
            setState(e.target.value as IncidentState | '');
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All states</MenuItem>
          {STATES.map((s) => (
            <MenuItem key={s} value={s}>
              {STATE_LABEL[s]}
            </MenuItem>
          ))}
        </TextField>
        <TextField size="small" label="Search number or text" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flexGrow: 1, maxWidth: 420 }} />
      </Stack>

      <ErrorAlert error={incidents.error} />

      <Paper>
        {incidents.isFetching && <LinearProgress />}
        <TableContainer>
          <Table size="small" stickyHeader aria-label="Incidents">
            <TableHead>
              <TableRow>
                <TableCell>Number</TableCell>
                <TableCell>Opened</TableCell>
                <TableCell>Short description</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>State</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Team</TableCell>
                <TableCell>Assigned to</TableCell>
                <TableCell>Updated</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((incident) => (
                <TableRow key={incident.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/incidents/${incident.id}`)}>
                  <TableCell>
                    <RouterLink to={`/incidents/${incident.id}`} onClick={(e) => e.stopPropagation()}>
                      {incident.number}
                    </RouterLink>
                  </TableCell>
                  <TableCell>{formatDateTime(incident.createdAt)}</TableCell>
                  <TableCell sx={{ minWidth: 260, maxWidth: 380 }}>{incident.shortDescription}</TableCell>
                  <TableCell>
                    <PriorityDot priority={incident.priority} />
                  </TableCell>
                  <TableCell>{STATE_LABEL[incident.state]}</TableCell>
                  <TableCell>{incident.category}</TableCell>
                  <TableCell>{teamName(incident.assignedTeamId)}</TableCell>
                  <TableCell>{incident.assignedToId == null ? '(empty)' : userName(incident.assignedToId)}</TableCell>
                  <TableCell>{formatDateTime(incident.updatedAt)}</TableCell>
                </TableRow>
              ))}
              {!incidents.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9}>
                    <EmptyState message="No incidents match." />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={incidents.data?.total ?? 0}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 20, 50]}
          labelDisplayedRows={({ from, to, count }) => `${from} to ${to} of ${count}`}
          onPageChange={(_, next) => setPage(next)}
          onRowsPerPageChange={(e) => {
            setSize(Number(e.target.value));
            setPage(0);
          }}
        />
      </Paper>
    </Stack>
  );
}
