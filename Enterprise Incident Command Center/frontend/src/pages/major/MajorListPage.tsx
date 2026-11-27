import { useState } from 'react';
import { EmptyState } from '../../components/illustrations';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { LinearProgress, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import { useMajorIncidents, type MajorStatus } from '../../api/majorIncidents';
import { useNames } from '../../api/lookups';
import { ErrorAlert, MajorStatusChip, formatDateTime } from '../../components/common';
import { MAJOR_STATUSES, MAJOR_STATUS_LABEL } from './rules';

/** All major incidents, newest first. A major incident is declared from the page of a priority 1 incident. */
export function MajorListPage() {
  const navigate = useNavigate();
  const { userName } = useNames();
  const [status, setStatus] = useState<MajorStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const major = useMajorIncidents({ status: status || undefined, page, size });
  const rows = major.data?.items ?? [];

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">
        Major incidents
      </Typography>
      <Typography color="text.secondary">A major incident is declared from the page of a priority 1 incident (the Escalate button).</Typography>

      <TextField
        select
        size="small"
        label="Status"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as MajorStatus | '');
          setPage(0);
        }}
        sx={{ width: 200 }}
      >
        <MenuItem value="">All statuses</MenuItem>
        {MAJOR_STATUSES.map((s) => (
          <MenuItem key={s} value={s}>
            {MAJOR_STATUS_LABEL[s]}
          </MenuItem>
        ))}
      </TextField>

      <ErrorAlert error={major.error} />

      <Paper>
        {major.isFetching && <LinearProgress />}
        <TableContainer>
          <Table size="small" stickyHeader aria-label="Major incidents">
            <TableHead>
              <TableRow>
                <TableCell>Major incident</TableCell>
                <TableCell>Summary</TableCell>
                <TableCell>Incident</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Commander</TableCell>
                <TableCell>Declared</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((m) => (
                <TableRow key={m.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/major-incidents/${m.id}`)}>
                  <TableCell>
                    <RouterLink to={`/major-incidents/${m.id}`} onClick={(e) => e.stopPropagation()}>
                      #{m.id}
                    </RouterLink>
                  </TableCell>
                  <TableCell sx={{ maxWidth: 360 }}>{m.summary}</TableCell>
                  <TableCell>
                    <RouterLink to={`/incidents/${m.incidentId}`} onClick={(e) => e.stopPropagation()}>
                      Incident {m.incidentId}
                    </RouterLink>
                  </TableCell>
                  <TableCell>
                    <MajorStatusChip status={m.status} />
                  </TableCell>
                  <TableCell>{userName(m.commanderId)}</TableCell>
                  <TableCell>{formatDateTime(m.declaredAt)}</TableCell>
                </TableRow>
              ))}
              {!major.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <EmptyState message="No major incidents match." />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={major.data?.total ?? 0}
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
