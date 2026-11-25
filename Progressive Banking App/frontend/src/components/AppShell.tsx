import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Brand } from './Brand'
import { Icon, type IconName } from './Icon'
import { ThemeToggle } from './ThemeToggle'
import { Avatar } from './ui'

/**
 * The logged-in frame: sidebar navigation on large screens, a slide-in drawer on phones, and a top bar.
 * Rendered by App.tsx as the parent route of every customer and admin page; the page itself appears in <Outlet />.
 * The navigation differs by role (CUSTOMER or ADMIN, from auth/AuthContext), but remember that hiding a link is
 * cosmetic - the backend enforces access.
 */
interface NavItem {
  to: string
  label: string
  icon: IconName
  end?: boolean
}

const customerLinks: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: 'home' },
  { to: '/accounts', label: 'My accounts', icon: 'wallet' },
  { to: '/transfer', label: 'Transfer money', icon: 'send' },
  { to: '/transactions', label: 'Transactions', icon: 'history' },
]

const adminLinks: NavItem[] = [
  { to: '/admin', label: 'Overview', icon: 'chart', end: true },
  { to: '/admin/accounts', label: 'Accounts', icon: 'users' },
]

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout } = useAuth()
  if (!user) return null
  const links = user.role === 'ADMIN' ? adminLinks : customerLinks

  return (
    <div className="flex h-full flex-col bg-brand-950 text-white">
      <div className="px-5 pb-6 pt-6">
        <Brand light />
      </div>

      <nav aria-label="Main" className="flex-1 space-y-1 px-3">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive ? 'bg-white/12 text-white' : 'text-white/65 hover:bg-white/8 hover:text-white'
              }`
            }
          >
            <Icon name={link.icon} className="size-5" />
            {link.label}
          </NavLink>
        ))}
      </nav>

      <div className="m-3 rounded-2xl bg-white/6 p-3">
        <div className="flex items-center gap-3">
          <Avatar name={user.fullName} />
          <div className="min-w-0 leading-tight">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="text-xs text-white/55">{user.role === 'ADMIN' ? 'Administrator' : 'Customer'}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={logout}
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-white/10 py-2 text-sm font-medium text-white/90 transition-colors hover:bg-white/16"
        >
          <Icon name="logout" className="size-4" /> Sign out
        </button>
      </div>

      <p className="flex items-center gap-1.5 px-5 pb-4 text-[11px] text-white/40">
        <Icon name="lock" className="size-3" /> Auto sign-out after 30 min
      </p>
    </div>
  )
}

export function AppShell() {
  const { user } = useAuth()
  const [drawerOpen, setDrawerOpen] = useState(false)

  // Escape closes the drawer; choosing a link closes it too (SidebarContent calls onNavigate).
  useEffect(() => {
    if (!drawerOpen) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setDrawerOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [drawerOpen])

  if (!user) return null

  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">
        <SidebarContent />
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="presentation">
          <div className="fade-in absolute inset-0 bg-[#050b1a]/60 backdrop-blur-sm" onClick={() => setDrawerOpen(false)} />
          <aside className="slide-right absolute inset-y-0 left-0 w-72 max-w-[85vw]" aria-label="Navigation">
            <SidebarContent onNavigate={() => setDrawerOpen(false)} />
          </aside>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-line bg-bg/85 px-4 py-3 backdrop-blur-md sm:px-8">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open navigation"
            className="grid size-10 place-items-center rounded-xl border border-line bg-surface text-muted lg:hidden"
          >
            <Icon name="menu" className="size-5" />
          </button>
          <div className="lg:hidden">
            <Brand />
          </div>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-semibold text-ink">{user.fullName}</p>
              <p className="text-xs text-muted">{user.role === 'ADMIN' ? 'Administrator' : `@${user.username}`}</p>
            </div>
            <ThemeToggle />
          </div>
        </header>

        <main className="mx-auto max-w-6xl px-4 py-7 sm:px-8 sm:py-9">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
