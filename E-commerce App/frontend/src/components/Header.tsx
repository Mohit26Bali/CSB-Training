import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { getCategories } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { useCart, useWishlist } from '../hooks'
import { SearchBox } from './SearchBox'

export function Logo() {
  return (
    <Link to="/" className="flex shrink-0 items-center gap-2 text-white" aria-label="CartNest home">
      <img src="/favicon.svg" alt="" className="h-8 w-8" />
      <span className="text-xl font-bold tracking-tight">
        Cart<span className="text-accent-400">Nest</span>
      </span>
    </Link>
  )
}

function CountBadge({ count }: { count: number }) {
  if (!count) return null
  return (
    <span className="absolute -right-2 -top-1.5 min-w-5 rounded-full bg-accent-400 px-1 text-center text-[11px] font-bold leading-5 text-slate-900">
      {count > 99 ? '99+' : count}
    </span>
  )
}

function AccountMenu() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const location = useLocation()

  useEffect(() => {
    setOpen(false)
  }, [location.pathname])
  useEffect(() => {
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  if (!user) {
    return (
      <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
        className="rounded-md bg-white px-5 py-1.5 text-sm font-semibold text-brand-700 hover:bg-brand-50">
        Login
      </Link>
    )
  }
  const firstName = user.fullName.split(' ')[0]
  const links = user.role === 'ADMIN'
    ? [{ to: '/admin', label: 'Admin dashboard' }]
    : [
        { to: '/account', label: 'My profile' },
        { to: '/orders', label: 'Orders' },
        { to: '/wishlist', label: 'Wishlist' },
        { to: '/account#addresses', label: 'Saved addresses' },
      ]
  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu"
        className="flex items-center gap-1 text-sm font-semibold text-white">
        <span className="max-w-28 truncate">{firstName}</span>
        <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor" aria-hidden>
          <path d="M5.5 7.5 10 12l4.5-4.5" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-52 overflow-hidden rounded-md bg-white py-1 text-sm text-slate-700 shadow-xl ring-1 ring-slate-200">
          <p className="truncate border-b border-slate-100 px-4 py-2 text-xs text-slate-500">{user.email}</p>
          {links.map((l) => (
            <Link key={l.to} to={l.to} role="menuitem" className="block px-4 py-2 hover:bg-slate-50">
              {l.label}
            </Link>
          ))}
          <button role="menuitem" onClick={logout} className="block w-full px-4 py-2 text-left text-red-600 hover:bg-red-50">
            Log out
          </button>
        </div>
      )}
    </div>
  )
}

/** Departments with a drop-down of their sub-categories. */
function CategoryBar() {
  const categories = useQuery({ queryKey: ['categories'], queryFn: getCategories, staleTime: 5 * 60_000 })
  const [openSlug, setOpenSlug] = useState<string | null>(null)
  const location = useLocation()
  useEffect(() => {
    setOpenSlug(null)
  }, [location.pathname, location.search])

  return (
    <nav aria-label="Departments" className="border-b border-slate-200 bg-white">
      <ul className="no-scrollbar mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 lg:overflow-visible">
        {(categories.data ?? []).map((dept) => (
          <li key={dept.slug} className="relative shrink-0"
            onMouseEnter={() => setOpenSlug(dept.slug)} onMouseLeave={() => setOpenSlug(null)}>
            <NavLink to={`/c/${dept.slug}`}
              className={({ isActive }) => `block whitespace-nowrap px-3 py-2.5 text-sm font-medium ${isActive ? 'text-brand-700' : 'text-slate-700 hover:text-brand-700'}`}>
              {dept.name}
            </NavLink>
            {openSlug === dept.slug && dept.children.length > 0 && (
              <div className="absolute left-0 top-full z-30 hidden w-60 rounded-b-md bg-white py-2 shadow-xl ring-1 ring-slate-200 lg:block">
                {dept.children.map((c) => (
                  <Link key={c.slug} to={`/c/${c.slug}`} className="flex justify-between px-4 py-1.5 text-sm text-slate-700 hover:bg-brand-50 hover:text-brand-700">
                    {c.name}
                    <span className="text-xs text-slate-400">{c.productCount}</span>
                  </Link>
                ))}
              </div>
            )}
          </li>
        ))}
      </ul>
    </nav>
  )
}

export function Header() {
  const { user } = useAuth()
  const cart = useCart()
  const wishlist = useWishlist()
  const isCustomer = user?.role === 'CUSTOMER'
  const iconLink = 'relative flex items-center gap-1.5 text-sm font-semibold text-white hover:text-accent-300'

  return (
    <header className="sticky top-0 z-30 shadow">
      <div className="bg-brand-700">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
          <Logo />
          <div className="order-last w-full md:order-none md:w-auto md:flex-1">
            <SearchBox />
          </div>
          <div className="ml-auto flex items-center gap-6 md:ml-0">
            <AccountMenu />
            {(!user || isCustomer) && (
              <>
                <Link to="/wishlist" className={iconLink} aria-label="Wishlist">
                  <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                    <path d="M12 20s-7-4.4-9.2-8.6C1.2 8.3 3 4.8 6.5 4.8c2 0 3.5 1.1 4.5 2.6 1-1.5 2.5-2.6 4.5-2.6 3.5 0 5.3 3.5 3.7 6.6C19 15.6 12 20 12 20Z" strokeLinejoin="round" />
                  </svg>
                  <CountBadge count={wishlist.data?.length ?? 0} />
                </Link>
                <Link to="/cart" className={iconLink} aria-label="Cart">
                  <span className="relative">
                    <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                      <path d="M3 4h2.5l2.3 10.5h10.1L20.5 8H7" strokeLinecap="round" strokeLinejoin="round" />
                      <circle cx="9.5" cy="19" r="1.5" />
                      <circle cx="17" cy="19" r="1.5" />
                    </svg>
                    <CountBadge count={cart.data?.summary.itemCount ?? 0} />
                  </span>
                  <span className="hidden sm:inline">Cart</span>
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
      <CategoryBar />
    </header>
  )
}
