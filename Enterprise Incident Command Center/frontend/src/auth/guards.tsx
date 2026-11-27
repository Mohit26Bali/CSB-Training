import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Alert, Typography } from '@mui/material';
import { useAuth } from './AuthContext';
import type { Role } from './access';

/** Pages behind this need a login. Not logged in: go to the login page and come back afterwards (not after an explicit Log out). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loggedOutByUser } = useAuth();
  const location = useLocation();
  if (!user) {
    // Remember the wanted page for deep links and expired sessions, but not after an explicit Log out.
    return <Navigate to="/login" replace state={loggedOutByUser ? undefined : { from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}

/** Pages behind this need one of the roles. The server checks again; this only gives a clear message sooner. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) {
    return (
      <>
        <Typography variant="h5" gutterBottom>
          Not available for your role
        </Typography>
        <Alert severity="warning">Your role does not have access to this page.</Alert>
      </>
    );
  }
  return <>{children}</>;
}
