import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { BookingsAdminPage } from './pages/admin/BookingsAdminPage'
import { CalendarPage } from './pages/admin/CalendarPage'
import { DashboardPage } from './pages/admin/DashboardPage'
import { RoomsAdminPage } from './pages/admin/RoomsAdminPage'
import { MyBookingsPage } from './pages/guest/MyBookingsPage'
import { SearchPage } from './pages/guest/SearchPage'
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
          <Route path="/rooms" element={<SearchPage />} />
          <Route path="/bookings" element={<MyBookingsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="HOTEL_ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/bookings" element={<BookingsAdminPage />} />
          <Route path="/admin/calendar" element={<CalendarPage />} />
          <Route path="/admin/rooms" element={<RoomsAdminPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
