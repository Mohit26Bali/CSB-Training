import { useState } from 'react';
import { EmptyState } from '../../components/illustrations';
import { Box, LinearProgress, Paper, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TablePagination, Tabs, Typography } from '@mui/material';
import { AUDIT_SOURCES, useAudit, type AuditSource } from '../../api/audit';
import { useNames } from '../../api/lookups';
import { ErrorAlert, formatDateTime } from '../../components/common';

/** "INCIDENT 12" for the record the event is about. */
const subject = (type: string, id?: number | null) => (id == null ? type : `${type} #${id}`);

/**
 * The audit trails, one tab per service (each service keeps its own, D11). Newest first.
 * The reference is the correlation id: the same value appears in the trails of every service one request passed through.
 */
export function AuditPage() {
  const { userName } = useNames();
  const [source, setSource] = useState<AuditSource>('incident');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const audit = useAudit(source, page, size);
  const rows = audit.data?.items ?? [];

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">
        Audit trails
      </Typography>
      <Typography color="text.secondary">Who did what, newest first. Each service keeps its own trail. Events from one request share a reference, so you can follow it across services.</Typography>
      <Paper>
        <Tabs
          value={source}
          onChange={(_, v: AuditSource) => {
            setSource(v);
            setPage(0);
          }}
          variant="scrollable"
          aria-label="Audit trail of"
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          {AUDIT_SOURCES.map((s) => (
            <Tab key={s.key} value={s.key} label={s.label} />
          ))}
        </Tabs>
        {audit.isFetching && <LinearProgress />}
        <Box sx={{ p: audit.error ? 2 : 0 }}>
          <ErrorAlert error={audit.error} />
        </Box>
        <TableContainer>
          <Table size="small" stickyHeader aria-label="Audit events">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Who</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Record</TableCell>
                <TableCell>Details</TableCell>
                <TableCell>Reference</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((e) => (
                <TableRow key={e.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(e.createdAt)}</TableCell>
                  <TableCell>{e.actorId == null ? 'System' : userName(e.actorId)}</TableCell>
                  <TableCell>{e.action}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{subject(e.entityType, e.entityId)}</TableCell>
                  <TableCell sx={{ minWidth: 220 }}>{e.details ?? ''}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }} title={e.correlationId ?? ''}>
                    {e.correlationId ? e.correlationId.slice(0, 8) : ''}
                  </TableCell>
                </TableRow>
              ))}
              {!audit.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <EmptyState message="No events yet." />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={audit.data?.total ?? 0}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[20, 50, 100]}
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
