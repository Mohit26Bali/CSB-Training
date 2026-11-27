import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { config } from '../config'
import { look } from '../look'
import { ThemeToggle } from './ThemeToggle'
import { Button } from './ui'

function PageBanner() {
  const banner = look.banners[useLocation().pathname]
  if (!banner) return null
  return (
    <div className="relative mb-6 overflow-hidden rounded-2xl">
      <img src={banner.image} alt="" className="h-32 w-full object-cover sm:h-40" />
      <div className="absolute inset-0 flex flex-col justify-end bg-linear-to-t from-black/70 via-black/20 to-transparent p-4 text-white sm:p-5">
        <p className="text-lg font-semibold sm:text-xl">{banner.title}</p>
        <p className="text-sm opacity-90">{banner.text}</p>
      </div>
    </div>
  )
}

function LogoMark({ className = 'size-5' }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {config.logo.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}

/** App frame. look.navStyle picks the arrangement: "top" (white bar), "brand" (coloured bar) or "side" (coloured sidebar). */
export function Layout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  if (!user) return null
  const links = config.nav[user.role]

  const person = (light: boolean) => (
    <div className="text-right leading-tight">
      <p className={`text-sm font-medium ${light ? 'text-white' : 'text-slate-800'}`}>{user.fullname}</p>
      <p className={`text-xs ${light ? 'text-white/70' : 'text-slate-500'}`}>{config.roleLabels[user.role]}</p>
    </div>
  )

  if (look.navStyle === 'side') {
    const item = ({ isActive }: { isActive: boolean }) =>
      `block rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-white/15 text-white' : 'text-white/75 hover:bg-white/10 hover:text-white'}`
    return (
      <div className="min-h-screen md:pl-60">
        <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col bg-brand-800 px-3 py-5 text-white md:flex">
          <div className="mb-6 flex items-center gap-2.5 px-2">
            <span className="grid size-9 place-items-center rounded-xl bg-white/15 ring-1 ring-white/25">
              <LogoMark />
            </span>
            <span className="text-base font-semibold tracking-tight">{config.appName}</span>
          </div>
          <nav aria-label="Main" className="flex-1 space-y-1">
            {links.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} className={item}>
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="space-y-3 border-t border-white/15 px-2 pt-4">
            <div className="text-left">{person(true)}</div>
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <Button variant="secondary" small onClick={logout}>
                Sign out
              </Button>
            </div>
          </div>
        </aside>

        <header className="border-b border-slate-200 bg-white md:hidden">
          <div className="flex items-center justify-between px-4 py-3">
            <span className="flex items-center gap-2 text-base font-semibold text-brand-800">
              <LogoMark />
              {config.appName}
            </span>
            <button type="button" aria-expanded={menuOpen} aria-label="Menu" onClick={() => setMenuOpen((o) => !o)} className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-700 ring-1 ring-slate-300">
              {menuOpen ? 'Close' : 'Menu'}
            </button>
          </div>
          {menuOpen && (
            <nav aria-label="Main" className="space-y-1 border-t border-slate-200 px-4 py-3">
              {links.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  end={link.end}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) => `block rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100'}`}
                >
                  {link.label}
                </NavLink>
              ))}
              <div className="flex items-center justify-between pt-2">
                <span className="text-sm text-slate-600">
                  {user.fullname} ({config.roleLabels[user.role]})
                </span>
                <div className="flex items-center gap-2">
                  <ThemeToggle />
                  <Button variant="secondary" small onClick={logout}>
                    Sign out
                  </Button>
                </div>
              </div>
            </nav>
          )}
        </header>

        <main className="mx-auto max-w-6xl px-4 py-8 md:px-8">
          <PageBanner />
          <Outlet />
        </main>
      </div>
    )
  }

  const brand = look.navStyle === 'brand'
  return (
    <div className="min-h-screen">
      <header className={brand ? 'bg-brand-800 text-white shadow-sm' : 'border-b border-slate-200 bg-white'}>
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-2 px-4 py-3">
          <span className={`flex items-center gap-2 text-base font-semibold ${brand ? 'text-white' : 'text-brand-800'}`}>
            <LogoMark />
            {config.appName}
          </span>
          <nav aria-label="Main" className="order-last flex w-full gap-1 overflow-x-auto sm:order-0 sm:w-auto">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${
                    brand
                      ? isActive
                        ? 'bg-white/15 text-white'
                        : 'text-white/80 hover:bg-white/10 hover:text-white'
                      : isActive
                        ? 'bg-brand-50 text-brand-800'
                        : 'text-slate-600 hover:bg-slate-100'
                  }`
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            {person(brand)}
            <ThemeToggle />
            <Button variant="secondary" small onClick={logout}>
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <PageBanner />
        <Outlet />
      </main>
    </div>
  )
}
