import { useState } from 'react';
import { Alert, Box, Button, CircularProgress, LinearProgress, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useRules, useUpdateRule, type Rule } from '../../api/rules';
import { useTeams } from '../../api/lookups';
import { ErrorAlert } from '../../components/common';

/** One row: the category, a team picker, and Save, which is on only when the choice differs from what is stored. */
function RuleRow({ rule, onSaved }: { rule: Rule; onSaved: (message: string) => void }) {
  const teams = useTeams();
  const update = useUpdateRule();
  const [teamId, setTeamId] = useState<number>(rule.teamId);
  const changed = teamId !== rule.teamId;
  const teamName = teams.data?.find((t) => t.id === teamId)?.name;

  return (
    <TableRow>
      <TableCell component="th" scope="row">
        {rule.category}
      </TableCell>
      <TableCell>
        <TextField select value={teamId} onChange={(e) => setTeamId(Number(e.target.value))} disabled={update.isPending || !teams.data} slotProps={{ select: { SelectDisplayProps: { 'aria-label': `Team for ${rule.category}` } as object } }} sx={{ minWidth: 220 }}>
          {(teams.data ?? []).map((t) => (
            <MenuItem key={t.id} value={t.id}>
              {t.name}
            </MenuItem>
          ))}
        </TextField>
      </TableCell>
      <TableCell>{rule.active ? 'Active' : 'Inactive'}</TableCell>
      <TableCell align="right">
        <Button
          variant="contained"
          size="small"
          disabled={!changed || update.isPending}
          aria-label={`Save ${rule.category}`}
          onClick={() => update.mutate({ id: rule.id, teamId }, { onSuccess: () => onSaved(`${rule.category} incidents now go to the ${teamName ?? 'chosen'} team.`) })}
        >
          {update.isPending ? <CircularProgress size={18} color="inherit" /> : 'Save'}
        </Button>
        {update.error && (
          <Box sx={{ mt: 1 }}>
            <ErrorAlert error={update.error} />
          </Box>
        )}
      </TableCell>
    </TableRow>
  );
}

export function RulesPage() {
  const rules = useRules();
  const [notice, setNotice] = useState<string | null>(null);

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">
        Assignment rules
      </Typography>
      <Typography color="text.secondary">Each category has one team. A new incident is routed to the team of its category. Changing a rule affects new incidents only; incidents already reported keep their team.</Typography>
      {notice && (
        <Alert severity="success" onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}
      <ErrorAlert error={rules.error} />
      <Paper>
        {rules.isLoading && <LinearProgress />}
        <TableContainer>
          <Table size="small" aria-label="Assignment rules">
            <TableHead>
              <TableRow>
                <TableCell>Category</TableCell>
                <TableCell>Team</TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {(rules.data ?? []).map((rule) => (
                <RuleRow key={`${rule.id}-${rule.teamId}`} rule={rule} onSaved={setNotice} />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Stack>
  );
}
