import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { ApplicationsPage } from './pages/admin/ApplicationsPage'
import { ProgramsAdminPage } from './pages/admin/ProgramsAdminPage'
import { StatisticsPage } from './pages/admin/StatisticsPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { MyApplicationsPage } from './pages/student/MyApplicationsPage'
import { ProgramsPage } from './pages/student/ProgramsPage'

function Home() {
  const { user } = useAuth()
  return <Navigate to={user ? homePathFor(user.role) : '/login'} replace />
}

function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 px-4 text-center">
      <p className="text-5xl font-semibold text-slate-300">404</p>
      <p className="text-slate-600">That page doesn’t exist.</p>
      <Link to="/" className="font-medium text-brand-700 hover:underline">
        Go home
      </Link>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<RequireRole role="STUDENT" />}>
        <Route element={<Layout />}>
          <Route path="/programs" element={<ProgramsPage />} />
          <Route path="/applications" element={<MyApplicationsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="SCHOLARSHIP_ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<StatisticsPage />} />
          <Route path="/admin/applications" element={<ApplicationsPage />} />
          <Route path="/admin/programs" element={<ProgramsAdminPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
