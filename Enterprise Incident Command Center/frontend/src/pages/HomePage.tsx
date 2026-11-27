import { Chip, List, ListItem, ListItemText, Paper, Skeleton, Stack, Typography } from '@mui/material';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Box, Button, TextField } from '@mui/material';
import { useIncidents } from '../api/incidents';
import { useNames } from '../api/lookups';
import { formatDateTime, timeAgo } from '../components/common';
import { STATE_LABEL } from './incidents/rules';
import { HeroIllustration } from '../components/illustrations';
import { InboxIcon, LifebuoyIcon } from '../components/icons';
import { NAVY, PALETTE } from '../theme';
import { useAuth } from '../auth/AuthContext';
import { ROLE_ABILITIES, ROLE_LABEL } from '../auth/access';

/** The employee's home: "How can we help?", two big doors (report, follow) and the latest incidents. */
function EmployeeHome() {
  const { user } = useAuth();
  const { loading, teamName } = useNames();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const mine = useIncidents({ scope: 'mine', q: '', page: 0, size: 5 });
  if (!user) return null;
  const go = (event: FormEvent) => {
    event.preventDefault();
    navigate(q.trim() ? `/incidents?q=${encodeURIComponent(q.trim())}` : '/incidents');
  };
  const door = (to: string, title: string, text: string, icon: ReactNode) => (
    <Paper component={RouterLink} to={to} sx={{ p: 2.5, textDecoration: 'none', color: 'inherit', display: 'flex', gap: 2, alignItems: 'center', transition: 'box-shadow .15s, transform .15s, border-color .15s', '&:hover': { borderColor: 'primary.main', boxShadow: '0 8px 24px rgba(48,65,75,0.14)', transform: 'translateY(-2px)' } }}>
      <Box sx={{ width: 52, height: 52, borderRadius: 2, bgcolor: PALETTE.paleSky, color: 'primary.main', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon}</Box>
      <Box>
        <Typography variant="h6" component="h3" color="primary">
          {title}
        </Typography>
        <Typography color="text.secondary">{text}</Typography>
      </Box>
    </Paper>
  );
  return (
    <Stack spacing={3}>
      <Box sx={{ borderRadius: 2, px: { xs: 3, md: 6 }, py: { xs: 4, md: 5 }, color: '#fff', display: 'flex', alignItems: 'center', gap: 4, overflow: 'hidden', background: `radial-gradient(circle at 85% 20%, rgba(124,152,179,0.55) 0%, rgba(124,152,179,0) 45%), linear-gradient(120deg, ${NAVY} 0%, ${PALETTE.blueSlate2} 100%)` }}>
        <Box sx={{ flex: '1 1 auto', minWidth: 0 }}>
        <Typography variant="h6" component="h1" sx={{ fontWeight: 400, color: PALETTE.paleSky2 }}>
          Welcome, {user.fullName}
        </Typography>
        <Typography variant="h3" component="h2" sx={{ fontWeight: 700, mb: 3 }}>
          How can we help?
        </Typography>
        <Box component="form" onSubmit={go} sx={{ maxWidth: 560 }}>
          <TextField fullWidth value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search your incidents" slotProps={{ htmlInput: { 'aria-label': 'Search your incidents' } }} sx={{ bgcolor: '#fff', borderRadius: 8, '& .MuiOutlinedInput-root': { borderRadius: 8 } }} />
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mt: 3 }}>
          <Chip label={ROLE_LABEL[user.role]} sx={{ bgcolor: PALETTE.paleSky2, color: '#1f2a33' }} />
          {loading ? <Skeleton width={120} sx={{ bgcolor: 'rgba(255,255,255,0.3)' }} /> : <Chip label={teamName(user.teamId)} variant="outlined" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)' }} />}
          <Typography sx={{ color: PALETTE.paleSky }}>logged in as {user.username}</Typography>
        </Stack>
        </Box>
        <Box sx={{ display: { xs: 'none', md: 'block' }, flex: '0 0 340px' }}>
          <HeroIllustration width={340} />
        </Box>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' } }}>
        {door('/incidents/new', 'Get help', 'Contact support to report a problem.', <LifebuoyIcon size={26} />)}
        {door('/incidents', 'Requests', 'See the incidents you reported and what is happening to them.', <InboxIcon size={26} />)}
      </Box>

      <Paper>
        <Typography variant="h6" component="h3" sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          Your latest incidents
        </Typography>
        {mine.data && mine.data.items.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            You have not reported an incident yet.
          </Typography>
        )}
        <Stack divider={<Box sx={{ borderTop: 1, borderColor: 'divider' }} />}>
          {(mine.data?.items ?? []).map((i) => (
            <Box key={i.id} sx={{ p: 2, display: 'flex', gap: 2, alignItems: 'baseline', flexWrap: 'wrap' }}>
              <RouterLink to={`/incidents/${i.id}`} style={{ flex: '1 1 260px' }}>
                {i.shortDescription}
              </RouterLink>
              <Typography variant="body2" color="text.secondary">
                {i.number}
              </Typography>
              <Typography variant="body2">{STATE_LABEL[i.state]}</Typography>
              <Typography variant="body2" color="text.secondary" title={formatDateTime(i.updatedAt)}>
                {timeAgo(i.updatedAt)}
              </Typography>
            </Box>
          ))}
        </Stack>
        {mine.data && mine.data.total > 5 && (
          <Box sx={{ p: 2 }}>
            <Button component={RouterLink} to="/incidents">
              See all {mine.data.total}
            </Button>
          </Box>
        )}
      </Paper>
    </Stack>
  );
}

/** The landing page after login: who you are, your team, and what your role can do. */
export function HomePage() {
  const { user } = useAuth();
  const { loading, teamName } = useNames();
  if (!user) return null;
  if (user.role === 'EMPLOYEE') return <EmployeeHome />;

  return (
    <Stack spacing={3}>
      <div>
        <Typography variant="h4" component="h1" gutterBottom>
          Welcome, {user.fullName}
        </Typography>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: "center", flexWrap: "wrap" }}>
          <Chip label={ROLE_LABEL[user.role]} color="primary" />
          {loading ? <Skeleton width={120} /> : <Chip label={teamName(user.teamId)} variant="outlined" />}
          <Typography color="text.secondary">logged in as {user.username}</Typography>
        </Stack>
      </div>

      <Paper sx={{ p: 2.5 }}>
        <Typography variant="h6" gutterBottom>
          What you can do as {ROLE_LABEL[user.role].toLowerCase()}
        </Typography>
        <List dense disablePadding>
          {ROLE_ABILITIES[user.role].map((ability) => (
            <ListItem key={ability} disableGutters>
              <ListItemText primary={ability} />
            </ListItem>
          ))}
        </List>
      </Paper>

    </Stack>
  );
}
