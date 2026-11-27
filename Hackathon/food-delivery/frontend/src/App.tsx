import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { CartProvider } from './cart/CartProvider'
import { Layout } from './components/Layout'
import { BoardPage } from './pages/admin/BoardPage'
import { MenuAdminPage } from './pages/admin/MenuAdminPage'
import { SalesPage } from './pages/admin/SalesPage'
import { MenuPage } from './pages/customer/MenuPage'
import { OrdersPage } from './pages/customer/OrdersPage'
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
    <CartProvider>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route element={<RequireRole role="CUSTOMER" />}>
          <Route element={<Layout />}>
            <Route path="/menu" element={<MenuPage />} />
            <Route path="/orders" element={<OrdersPage />} />
          </Route>
        </Route>

        <Route element={<RequireRole role="RESTAURANT_ADMIN" />}>
          <Route element={<Layout />}>
            <Route path="/admin" element={<BoardPage />} />
            <Route path="/admin/menu" element={<MenuAdminPage />} />
            <Route path="/admin/sales" element={<SalesPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </CartProvider>
  )
}
