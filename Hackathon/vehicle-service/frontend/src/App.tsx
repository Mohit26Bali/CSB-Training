import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/admin/DashboardPage'
import { SchedulePage } from './pages/admin/SchedulePage'
import { ServicesAdminPage } from './pages/admin/ServicesAdminPage'
import { VehiclesAdminPage } from './pages/admin/VehiclesAdminPage'
import { BookPage } from './pages/customer/BookPage'
import { MyServicesPage } from './pages/customer/MyServicesPage'
import { VehiclesPage } from './pages/customer/VehiclesPage'
import { LoginPage } from './pages/LoginPage'
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
          <Route path="/services" element={<MyServicesPage />} />
          <Route path="/book" element={<BookPage />} />
          <Route path="/vehicles" element={<VehiclesPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="SERVICE_ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/schedule" element={<SchedulePage />} />
          <Route path="/admin/vehicles" element={<VehiclesAdminPage />} />
          <Route path="/admin/services" element={<ServicesAdminPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
