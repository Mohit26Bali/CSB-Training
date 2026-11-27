import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { AssetsPage } from './pages/admin/AssetsPage'
import { DashboardPage } from './pages/admin/DashboardPage'
import { ReturnsPage } from './pages/admin/ReturnsPage'
import { HistoryPage } from './pages/employee/HistoryPage'
import { MyAssetsPage } from './pages/employee/MyAssetsPage'
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

      <Route element={<RequireRole role="EMPLOYEE" />}>
        <Route element={<Layout />}>
          <Route path="/my-assets" element={<MyAssetsPage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="ASSET_ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/assets" element={<AssetsPage />} />
          <Route path="/admin/returns" element={<ReturnsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
