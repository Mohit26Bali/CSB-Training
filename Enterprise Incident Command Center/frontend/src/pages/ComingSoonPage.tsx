import { Alert, Typography } from '@mui/material';

/** Stands in for a screen that is planned but not built yet. It keeps the route, the menu entry and the role guard real. */
export function ComingSoonPage({ title }: { title: string }) {
  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        {title}
      </Typography>
      <Alert severity="info">This screen is not built yet. It comes in one of the next phases (see docs/FRONTEND-PLAN.md).</Alert>
    </>
  );
}

export function NotFoundPage() {
  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        Page not found
      </Typography>
      <Alert severity="warning">This address does not exist.</Alert>
    </>
  );
}
