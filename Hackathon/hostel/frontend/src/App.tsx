import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminRequestsPage } from './pages/admin/AdminRequestsPage'
import { AdminRoomsPage } from './pages/admin/AdminRoomsPage'
import { ResidentsPage } from './pages/admin/ResidentsPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { MyRoomPage } from './pages/student/MyRoomPage'
import { RoomsPage } from './pages/student/RoomsPage'

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
          <Route path="/my-room" element={<MyRoomPage />} />
          <Route path="/rooms" element={<RoomsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="HOSTEL_ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/requests" element={<AdminRequestsPage />} />
          <Route path="/admin/rooms" element={<AdminRoomsPage />} />
          <Route path="/admin/residents" element={<ResidentsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
