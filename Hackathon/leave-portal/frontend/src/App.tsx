import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { CalendarPage } from './pages/manager/CalendarPage'
import { ReviewPage } from './pages/manager/ReviewPage'
import { MyLeavePage } from './pages/MyLeavePage'
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

      {/* Managers take leave too, so "My leave" is open to both roles */}
      <Route element={<RequireRole role={['EMPLOYEE', 'MANAGER']} />}>
        <Route element={<Layout />}>
          <Route path="/leave" element={<MyLeavePage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="MANAGER" />}>
        <Route element={<Layout />}>
          <Route path="/manager/review" element={<ReviewPage />} />
          <Route path="/manager/calendar" element={<CalendarPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
