import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { AgentDashboardPage } from './pages/agent/AgentDashboardPage'
import { QueuePage } from './pages/agent/QueuePage'
import { MyTicketsPage } from './pages/employee/MyTicketsPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { TicketPage } from './pages/TicketPage'

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

      <Route element={<RequireRole role={['EMPLOYEE', 'SUPPORT_AGENT']} />}>
        <Route element={<Layout />}>
          <Route path="/tickets/:id" element={<TicketPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="EMPLOYEE" />}>
        <Route element={<Layout />}>
          <Route path="/tickets" element={<MyTicketsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="SUPPORT_AGENT" />}>
        <Route element={<Layout />}>
          <Route path="/agent" element={<AgentDashboardPage />} />
          <Route path="/agent/queue" element={<QueuePage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
