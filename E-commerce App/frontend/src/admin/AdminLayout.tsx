import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui'

const NAV = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/products', label: 'Products' },
  { to: '/admin/catalogue', label: 'Categories & brands' },
  { to: '/admin/coupons', label: 'Coupons' },
  { to: '/admin/customers', label: 'Customers' },
]

/** The admin area: a sidebar of sections (a drop-down on phones) and the current section. */
export function AdminLayout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  useEffect(() => {
    setMenuOpen(false)
    window.scrollTo(0, 0)
  }, [pathname])

  return (
    <div className="min-h-screen bg-slate-100 lg:grid lg:grid-cols-[230px_1fr]">
      <aside className="bg-slate-900 text-slate-300 lg:sticky lg:top-0 lg:h-screen">
        <div className="flex items-center justify-between px-5 py-4">
          <Link to="/admin" className="text-lg font-bold text-white">
            Cart<span className="text-accent-400">Nest</span> <span className="text-xs font-medium text-slate-400">Admin</span>
          </Link>
          <button className="rounded px-2 py-1 text-sm text-white ring-1 ring-slate-600 lg:hidden" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
            Menu
          </button>
        </div>
        <nav aria-label="Admin sections" className={`${menuOpen ? 'block' : 'hidden'} px-3 pb-4 lg:block`}>
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}
              className={({ isActive }) => `block rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-600 text-white' : 'hover:bg-slate-800 hover:text-white'}`}>
              {item.label}
            </NavLink>
          ))}
          <div className="mt-6 border-t border-slate-800 px-3 pt-4 text-xs">
            <p className="truncate text-slate-400">{user?.email}</p>
            <div className="mt-2 flex gap-3">
              <Link to="/" className="font-semibold text-slate-200 hover:text-white">View store</Link>
              <button onClick={logout} className="font-semibold text-red-300 hover:text-red-200">Log out</button>
            </div>
          </div>
        </nav>
      </aside>
      <main className="min-w-0 p-4 sm:p-6">
        <Outlet />
      </main>
    </div>
  )
}

export function PageHeader({ title, actions, children }: { title: string; actions?: ReactNode; children?: ReactNode }) {
  return (
    <div className="mb-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {actions}
      </div>
      {children}
    </div>
  )
}

export function Pager({ page, totalPages, totalItems, onPage }: { page: number; totalPages: number; totalItems: number; onPage: (p: number) => void }) {
  return (
    <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-600">
      <span>{totalItems.toLocaleString('en-IN')} total</span>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="outline" className="px-3 py-1.5" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
          <span>Page {page + 1} of {totalPages}</span>
          <Button variant="outline" className="px-3 py-1.5" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</Button>
        </div>
      )}
    </div>
  )
}

/** An on/off switch. */
export function Toggle({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled} onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition disabled:opacity-50 ${checked ? 'bg-green-600' : 'bg-slate-300'}`}>
      <span className={`inline-block h-5 w-5 rounded-full bg-white shadow transition ${checked ? 'translate-x-5' : 'translate-x-0.5'}`} />
    </button>
  )
}

export const th = 'px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500'
export const td = 'px-4 py-3 align-middle'
