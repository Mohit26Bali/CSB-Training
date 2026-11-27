import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import RequireRole from './auth/RequireRole'
import AdminUsersPage from './pages/AdminUsersPage'
import ArenaPage from './pages/ArenaPage'
import DashboardPage from './pages/DashboardPage'
import HomePage from './pages/HomePage'
import { useAuth } from './auth/useAuth'
import LoginPage from './pages/LoginPage'
import ProgressPage from './pages/ProgressPage'
import QuestionEditorPage from './pages/QuestionEditorPage'
import QuizPage from './pages/QuizPage'
import RegisterPage from './pages/RegisterPage'
import ResultPage from './pages/ResultPage'
import TeacherQuestionsPage from './pages/TeacherQuestionsPage'

// The learning modules (and React Flow) are loaded only when a learner opens one, keeping them out of the main bundle.
const RestLifecyclePage = lazy(() => import('./learning/restLifecycle/RestLifecyclePage'))
const JwtSimulatorPage = lazy(() => import('./learning/jwtSecurity/JwtSimulatorPage'))
const JpaRelationshipPage = lazy(() => import('./learning/jpaLab/JpaRelationshipPage'))
const JdbcLabPage = lazy(() => import('./learning/jdbcLab/JdbcLabPage'))
const MicroLabPage = lazy(() => import('./learning/microLab/MicroLabPage'))
const CodingCataloguePage = lazy(() => import('./coding/CodingCataloguePage'))
const CodingProblemPage = lazy(() => import('./coding/CodingProblemPage'))
const CodingSubmissionPage = lazy(() => import('./coding/CodingSubmissionPage'))
const CodingProgressPage = lazy(() => import('./coding/CodingProgressPage'))

/** "/" is the dashboard for a signed-in user and the public home page for everyone else. */
function Root() {
  const { user, loading } = useAuth()
  if (loading) return <p className="p-6 text-fg-2">Checking your session…</p>
  return user ? <DashboardPage /> : <HomePage />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Root />} />
      <Route path="/welcome" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/arena" element={<ArenaPage />} />
        <Route path="/arena/attempts/:id" element={<QuizPage />} />
        <Route path="/arena/attempts/:id/result" element={<ResultPage />} />
        <Route path="/progress" element={<ProgressPage />} />
        <Route
          path="/learn/jwt-security"
          element={
            <Suspense fallback={<p className="p-6 text-muted">Loading lesson…</p>}>
              <JwtSimulatorPage />
            </Suspense>
          }
        />
        <Route
          path="/learn/rest-lifecycle"
          element={
            <Suspense fallback={<p className="p-6 text-muted">Loading lesson…</p>}>
              <RestLifecyclePage />
            </Suspense>
          }
        />
        <Route
          path="/learn/jpa-relationships"
          element={
            <Suspense fallback={<p className="p-6 text-muted">Loading lesson…</p>}>
              <JpaRelationshipPage />
            </Suspense>
          }
        />
        <Route
          path="/learn/jdbc"
          element={
            <Suspense fallback={<p className="p-6 text-muted">Loading lesson…</p>}>
              <JdbcLabPage />
            </Suspense>
          }
        />
        <Route
          path="/learn/microservices"
          element={
            <Suspense fallback={<p className="p-6 text-muted">Loading lesson…</p>}>
              <MicroLabPage />
            </Suspense>
          }
        />
        {/* Coding practice: open to every signed-in role; the API scopes everything to the caller. Static paths first. */}
        {[
          ['/learn/coding', CodingCataloguePage],
          ['/learn/coding/progress', CodingProgressPage],
          ['/learn/coding/submissions/:id', CodingSubmissionPage],
          ['/learn/coding/:slug', CodingProblemPage],
        ].map(([path, Page]) => (
          <Route
            key={path}
            path={path}
            element={
              <Suspense fallback={<p className="p-6 text-muted">Loading coding practice…</p>}>
                <Page />
              </Suspense>
            }
          />
        ))}
        {/* Route guards are UX only; every API behind these pages is authorized again by the backend. */}
        <Route element={<RequireRole roles={['TEACHER', 'ADMIN']} />}>
          <Route path="/teacher/questions" element={<TeacherQuestionsPage />} />
          <Route path="/teacher/questions/new" element={<QuestionEditorPage />} />
          <Route path="/teacher/questions/:id/edit" element={<QuestionEditorPage />} />
        </Route>
        <Route element={<RequireRole roles={['ADMIN']} />}>
          <Route path="/admin/users" element={<AdminUsersPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
