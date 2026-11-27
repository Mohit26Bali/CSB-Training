import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { ApiError } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { CheckIcon } from '../components/icons';
import { HeroIllustration } from '../components/illustrations';
import { NAVY, PAGE, PALETTE } from '../theme';

/** What the product does, in three short promises (shown beside the login form on a wide screen). */
const PROMISES = ['Report a problem in under a minute', 'The right team is picked for you', 'Major incidents get a commander and a live timeline'];

/** The only page that needs no login. After a successful login the user goes to the page they wanted, or Home. */
export function LoginPage() {
  const { user, notice, login } = useAuth();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | Error | null>(null);

  if (user) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (e) {
      setError(e instanceof Error ? e : new Error('Login failed'));
    } finally {
      setBusy(false);
    }
  };

  const unavailable = error instanceof ApiError && error.isUnavailable;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex' }}>
      {/* Left: the pitch. Hidden on a phone, where the form alone is shown. The photo is under a deep blue wash so the text stays readable. */}
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flex: '1 1 56%',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 4,
          px: { md: 6, lg: 10 },
          py: 6,
          color: '#fff',
          backgroundImage: `linear-gradient(135deg, rgba(48,65,75,0.96) 0%, rgba(48,65,75,0.86) 45%, rgba(83,107,120,0.72) 100%), url(/images/server-blades.jpg)`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      >
        <Box>
          <Typography component="div" sx={{ letterSpacing: 2, fontSize: 13, fontWeight: 700, color: PALETTE.paleSky2, mb: 1.5 }}>
            ENTERPRISE INCIDENT COMMAND CENTER
          </Typography>
          <Typography variant="h3" component="h2" sx={{ fontWeight: 700, lineHeight: 1.15, maxWidth: 560 }}>
            Keep every incident <Box component="span" sx={{ color: '#63d471' }}>under control</Box>.
          </Typography>
          <Stack spacing={1.25} sx={{ mt: 3 }}>
            {PROMISES.map((text) => (
              <Stack key={text} direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
                <Box sx={{ color: '#63d471', display: 'inline-flex' }}>
                  <CheckIcon size={20} />
                </Box>
                <Typography sx={{ color: PALETTE.paleSky }}>{text}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
        <HeroIllustration width={440} />
      </Box>

      {/* Right: the form. */}
      <Box sx={{ flex: '1 1 44%', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 3, bgcolor: PAGE, background: { xs: `linear-gradient(160deg, ${NAVY} 0%, ${PALETTE.blueSlate2} 100%)`, md: PAGE } }}>
        <Paper component="form" onSubmit={submit} sx={{ p: 4, width: '100%', maxWidth: 400, borderTop: 4, borderTopColor: PALETTE.blueSlate2, boxShadow: '0 10px 30px rgba(48,65,75,0.12)' }}>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h5" component="h1">
                Incident Command Center
              </Typography>
              <Typography color="text.secondary">Log in to continue</Typography>
            </Box>

            {notice && <Alert severity="info">{notice}</Alert>}
            {error && (
              <Alert severity={unavailable ? 'warning' : 'error'}>
                {error.message}
                {error instanceof ApiError && error.correlationId ? ` (reference ${error.correlationId})` : ''}
              </Alert>
            )}

            <TextField label="Email" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
            <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
            <Button type="submit" variant="contained" size="large" disabled={busy || !username.trim() || !password}>
              {busy ? <CircularProgress size={24} color="inherit" /> : 'Log in'}
            </Button>
          </Stack>
        </Paper>
      </Box>
    </Box>
  );
}
