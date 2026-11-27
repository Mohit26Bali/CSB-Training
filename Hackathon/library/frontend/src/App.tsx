import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { homePathFor, RequireRole } from './auth/RequireRole'
import { Layout } from './components/Layout'
import { BooksPage } from './pages/librarian/BooksPage'
import { DeskPage } from './pages/librarian/DeskPage'
import { LoansPage } from './pages/librarian/LoansPage'
import { LoginPage } from './pages/LoginPage'
import { CataloguePage } from './pages/member/CataloguePage'
import { ShelfPage } from './pages/member/ShelfPage'
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

      <Route element={<RequireRole role="MEMBER" />}>
        <Route element={<Layout />}>
          <Route path="/catalogue" element={<CataloguePage />} />
          <Route path="/shelf" element={<ShelfPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="LIBRARIAN" />}>
        <Route element={<Layout />}>
          <Route path="/desk" element={<DeskPage />} />
          <Route path="/desk/loans" element={<LoansPage />} />
          <Route path="/desk/books" element={<BooksPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
