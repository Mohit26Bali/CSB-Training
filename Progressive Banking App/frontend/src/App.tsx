import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor } from './auth/homePath'
import { RequireRole } from './auth/RequireRole'
import { AppShell } from './components/AppShell'
import { AdminAccountsPage } from './pages/admin/AdminAccountsPage'
import { OverviewPage } from './pages/admin/OverviewPage'
import { LoginPage } from './pages/auth/LoginPage'
import { RegisterPage } from './pages/auth/RegisterPage'
import { AccountDetailPage } from './pages/customer/AccountDetailPage'
import { AccountsPage } from './pages/customer/AccountsPage'
import { DashboardPage } from './pages/customer/DashboardPage'
import { TransactionsPage } from './pages/customer/TransactionsPage'
import { TransferPage } from './pages/customer/TransferPage'
import { NotFound } from './pages/NotFound'

/**
 * The route table. Three groups:
 *   public   /login, /register
 *   customer (RequireRole CUSTOMER) inside AppShell: /dashboard, /accounts, /accounts/:no, /transfer, /transactions
 *   admin    (RequireRole ADMIN)    inside AppShell: /admin, /admin/accounts
 * "/" sends people to the right place for their role. Guards here are for navigation only; the backend enforces access.
 */
function Home() {
  const { user } = useAuth()
  return <Navigate to={user ? homePathFor(user.role) : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<RequireRole role="CUSTOMER" />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/accounts/:accountNumber" element={<AccountDetailPage />} />
          <Route path="/transfer" element={<TransferPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="ADMIN" />}>
        <Route element={<AppShell />}>
          <Route path="/admin" element={<OverviewPage />} />
          <Route path="/admin/accounts" element={<AdminAccountsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
