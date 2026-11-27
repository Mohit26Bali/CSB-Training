import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { MyAppointmentsPage } from './pages/customer/MyAppointmentsPage'
import { ProviderPage } from './pages/customer/ProviderPage'
import { ProvidersPage } from './pages/customer/ProvidersPage'
import { LoginPage } from './pages/LoginPage'
import { AppointmentsPage } from './pages/provider/AppointmentsPage'
import { CalendarPage } from './pages/provider/CalendarPage'
import { DashboardPage } from './pages/provider/DashboardPage'
import { ProfilePage } from './pages/provider/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'

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

      <Route element={<RequireRole role="CUSTOMER" />}>
        <Route element={<Layout />}>
          <Route path="/providers" element={<ProvidersPage />} />
          <Route path="/providers/:id" element={<ProviderPage />} />
          <Route path="/appointments" element={<MyAppointmentsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="SERVICE_PROVIDER" />}>
        <Route element={<Layout />}>
          <Route path="/provider" element={<DashboardPage />} />
          <Route path="/provider/calendar" element={<CalendarPage />} />
          <Route path="/provider/appointments" element={<AppointmentsPage />} />
          <Route path="/provider/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
