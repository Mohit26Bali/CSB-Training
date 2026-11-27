import { Link, Route, Routes } from 'react-router-dom'
import { AdminLayout } from './admin/AdminLayout'
import { CatalogueAdminPage } from './admin/CatalogueAdmin'
import { CouponsAdminPage } from './admin/CouponsAdmin'
import { CustomersAdminPage } from './admin/CustomersAdmin'
import { DashboardPage } from './admin/DashboardPage'
import { OrderAdminPage, OrdersAdminPage } from './admin/OrdersAdmin'
import { ProductEditPage, ProductsAdminPage } from './admin/ProductsAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { AccountPage } from './pages/AccountPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { HomePage } from './pages/HomePage'
import { ListingPage } from './pages/ListingPage'
import { OrderDetailPage, OrdersPage } from './pages/OrderPages'
import { ProductPage } from './pages/ProductPage'
import { WishlistPage } from './pages/WishlistPage'

function NotFound() {
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <p className="text-6xl font-bold text-slate-300">404</p>
      <p className="mt-2 text-slate-600">We couldn't find that page.</p>
      <Link to="/" className="mt-4 font-semibold text-brand-700">Go to the home page</Link>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth role="ADMIN" />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="orders" element={<OrdersAdminPage />} />
          <Route path="orders/:number" element={<OrderAdminPage />} />
          <Route path="products" element={<ProductsAdminPage />} />
          <Route path="products/:id" element={<ProductEditPage />} />
          <Route path="catalogue" element={<CatalogueAdminPage />} />
          <Route path="coupons" element={<CouponsAdminPage />} />
          <Route path="customers" element={<CustomersAdminPage />} />
        </Route>
      </Route>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/c/:slug" element={<ListingPage />} />
        <Route path="/search" element={<ListingPage />} />
        <Route path="/p/:slug" element={<ProductPage />} />
        <Route element={<RequireAuth role="CUSTOMER" />}>
          <Route path="/cart" element={<CartPage />} />
          <Route path="/wishlist" element={<WishlistPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:number" element={<OrderDetailPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route path="/account" element={<AccountPage />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
