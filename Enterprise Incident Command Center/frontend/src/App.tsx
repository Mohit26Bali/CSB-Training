import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router-dom';
import { RequireAuth, RequireRole } from './auth/guards';
import { guardedPages } from './auth/access';
import { AppShell } from './layout/AppShell';
import { ComingSoonPage, NotFoundPage } from './pages/ComingSoonPage';
import { AuditPage } from './pages/admin/AuditPage';
import { DashboardPage } from './pages/admin/DashboardPage';
import { RulesPage } from './pages/admin/RulesPage';
import { HomePage } from './pages/HomePage';
import { IncidentDetailPage } from './pages/incidents/IncidentDetailPage';
import { IncidentListPage } from './pages/incidents/IncidentListPage';
import { IncidentNewPage } from './pages/incidents/IncidentNewPage';
import { LoginPage } from './pages/LoginPage';
import { ProblemDetailPage } from './pages/problems/ProblemDetailPage';
import { ProblemListPage } from './pages/problems/ProblemListPage';
import { ProblemNewPage } from './pages/problems/ProblemNewPage';
import { MajorDetailPage } from './pages/major/MajorDetailPage';
import { MajorListPage } from './pages/major/MajorListPage';

/** Pages that are built. Every other guarded address still shows the "not built yet" page. */
const BUILT: Record<string, ReactElement> = {
  '/dashboard': <DashboardPage />,
  '/admin/rules': <RulesPage />,
  '/admin/audit': <AuditPage />,
  '/incidents': <IncidentListPage />,
  '/incidents/new': <IncidentNewPage />,
  '/major-incidents': <MajorListPage />,
  '/problems': <ProblemListPage />,
};

/**
 * The route table. /login is open. Everything else needs a login and sits inside the shell (top bar and menu).
 * The pages that are not built yet (F2 to F5) already have their address and their role guard, so the menu,
 * the guards and the deep links are real; only the page content is missing.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<HomePage />} />
        {guardedPages().map((page) => (
          <Route
            key={page.path}
            path={page.path}
            element={
              <RequireRole roles={page.roles}>
                {BUILT[page.path] ?? <ComingSoonPage title={page.label} />}
              </RequireRole>
            }
          />
        ))}
        <Route
          path="/incidents/:id"
          element={
            <RequireRole roles={['EMPLOYEE', 'SUPPORT_ENGINEER', 'ADMIN']}>
              <IncidentDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="/major-incidents/:id"
          element={
            <RequireRole roles={['SUPPORT_ENGINEER', 'ADMIN']}>
              <MajorDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="/problems/new"
          element={
            <RequireRole roles={['SUPPORT_ENGINEER', 'ADMIN']}>
              <ProblemNewPage />
            </RequireRole>
          }
        />
        <Route
          path="/problems/:id"
          element={
            <RequireRole roles={['SUPPORT_ENGINEER', 'ADMIN']}>
              <ProblemDetailPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
