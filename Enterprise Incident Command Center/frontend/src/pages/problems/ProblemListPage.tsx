import { useState } from 'react';
import { EmptyState } from '../../components/illustrations';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Button, LinearProgress, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material';
import { useNames } from '../../api/lookups';
import { useProblems, type ProblemStatus } from '../../api/problems';
import { useAuth } from '../../auth/AuthContext';
import { ErrorAlert, PriorityChip, ProblemStatusChip, formatDateTime } from '../../components/common';
import { PROBLEM_STATUSES, PROBLEM_STATUS_LABEL, canCreateProblem } from './rules';

/** All problems, newest first. A problem records the root cause (RCA) of one or more incidents. */
export function ProblemListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { userName } = useNames();
  const [status, setStatus] = useState<ProblemStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const problems = useProblems({ status: status || undefined, page, size });
  if (!user) return null;
  const rows = problems.data?.items ?? [];

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }} useFlexGap>
        <Typography variant="h5" component="h1">
          {user.role === 'ADMIN' ? 'Problems and RCA' : 'Problems'}
        </Typography>
        {canCreateProblem(user) && (
          <Button variant="contained" component={RouterLink} to="/problems/new">
            New problem
          </Button>
        )}
      </Stack>

      <TextField
        select
        size="small"
        label="Status"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as ProblemStatus | '');
          setPage(0);
        }}
        sx={{ width: 200 }}
      >
        <MenuItem value="">All statuses</MenuItem>
        {PROBLEM_STATUSES.map((s) => (
          <MenuItem key={s} value={s}>
            {PROBLEM_STATUS_LABEL[s]}
          </MenuItem>
        ))}
      </TextField>

      <ErrorAlert error={problems.error} />

      <Paper>
        {problems.isFetching && <LinearProgress />}
        <TableContainer>
          <Table size="small" stickyHeader aria-label="Problems">
            <TableHead>
              <TableRow>
                <TableCell>Number</TableCell>
                <TableCell>Title</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Assigned to</TableCell>
                <TableCell>Major incident</TableCell>
                <TableCell>Created by</TableCell>
                <TableCell>Created</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((p) => (
                <TableRow key={p.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/problems/${p.id}`)}>
                  <TableCell>
                    <RouterLink to={`/problems/${p.id}`} onClick={(e) => e.stopPropagation()}>
                      {p.number}
                    </RouterLink>
                  </TableCell>
                  <TableCell sx={{ maxWidth: 320 }}>{p.title}</TableCell>
                  <TableCell>
                    <ProblemStatusChip status={p.status} />
                  </TableCell>
                  <TableCell>
                    <PriorityChip priority={p.priority} />
                  </TableCell>
                  <TableCell>{p.assignedToId == null ? '-' : userName(p.assignedToId)}</TableCell>
                  <TableCell>
                    {p.majorIncidentId == null ? (
                      '-'
                    ) : (
                      <RouterLink to={`/major-incidents/${p.majorIncidentId}`} onClick={(e) => e.stopPropagation()}>
                        #{p.majorIncidentId}
                      </RouterLink>
                    )}
                  </TableCell>
                  <TableCell>{userName(p.createdById)}</TableCell>
                  <TableCell>{formatDateTime(p.createdAt)}</TableCell>
                </TableRow>
              ))}
              {!problems.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8}>
                    <EmptyState message="No problems match." />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={problems.data?.total ?? 0}
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
