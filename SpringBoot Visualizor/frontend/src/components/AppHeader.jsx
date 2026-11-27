import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import Icon from './ui/Icon'
import StatusBadge from './ui/StatusBadge'

/** The lessons. They live in one menu so the bar stays short as lessons are added. */
const LEARN = [
  { to: '/learn/rest-lifecycle', label: 'REST request lifecycle', hint: 'Security, MVC, service, Hibernate' },
  { to: '/learn/jwt-security', label: 'JWT security', hint: '401 versus 403, token faults' },
  { to: '/learn/jpa-relationships', label: 'JPA relationship lab', hint: 'Owning side, cascade, fetching, N+1' },
  { to: '/learn/jdbc', label: 'JDBC lab', hint: 'Statements, transactions, pools, Spring JDBC' },
  { to: '/learn/microservices', label: 'Microservices lab', hint: 'Calls, events, sagas, resilience, tracing' },
  { to: '/learn/coding', label: 'Coding practice', hint: 'Write Java, run samples, submit for judging' },
]

const linkBase =
  'flex h-14 items-center border-b-2 px-3 text-sm font-medium transition-colors focus:outline-none focus-visible:bg-hover'
const linkClass = ({ isActive }) =>
  `${linkBase} ${isActive ? 'border-accent text-fg' : 'border-transparent text-fg-2 hover:text-fg'}`

/** Closes a menu on Escape and on a click or focus move outside it. */
function useDismiss(ref, open, close) {
  useEffect(() => {
    if (!open) return undefined
    const onPointer = (e) => {
      if (ref.current && !ref.current.contains(e.target)) close()
    }
    const onKey = (e) => {
      if (e.key === 'Escape') {
        close()
        ref.current?.querySelector('button')?.focus()
      }
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, ref, close])
}

/** A button that opens a small list of links. The button is marked active when the current page is one of them. */
function NavMenu({ label, items }) {
  const { pathname } = useLocation()
  // "Open" is remembered together with the page it was opened on, so navigating closes the menu without an effect.
  const [openedOn, setOpenedOn] = useState(null)
  const open = openedOn === pathname
  const ref = useRef(null)
  const close = () => setOpenedOn(null)
  useDismiss(ref, open, close)
  const active = items.some((i) => pathname.startsWith(i.to))
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpenedOn(open ? null : pathname)}
        className={`${linkBase} gap-1 ${active ? 'border-accent text-fg' : 'border-transparent text-fg-2 hover:text-fg'}`}
      >
        {label}
        <Icon name="chevron" className={`h-4 w-4 transition-transform motion-reduce:transition-none ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <ul className="absolute left-0 top-full z-40 mt-1 w-72 rounded-xl border border-line bg-elevated p-1 shadow-lg shadow-black/40" aria-label={label}>
          {items.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                className={({ isActive }) =>
                  `block rounded-lg px-3 py-2 text-sm hover:bg-hover focus:outline-none focus-visible:bg-hover ${isActive ? 'text-accent' : 'text-fg'}`
                }
              >
                <span className="block font-medium">{item.label}</span>
                {item.hint && <span className="block text-xs text-muted">{item.hint}</span>}
              </NavLink>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AccountMenu({ user, onLogout }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const close = () => setOpen(false)
  useDismiss(ref, open, close)
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={`Account menu for ${user.username}`}
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-lg border border-line px-2.5 py-1.5 text-sm text-fg hover:bg-hover"
      >
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-accent text-xs font-bold text-on-accent" aria-hidden="true">
          {user.username.slice(0, 1).toUpperCase()}
        </span>
        <span className="max-w-32 truncate">{user.username}</span>
        <Icon name="chevron" className="h-4 w-4 text-muted" />
      </button>
      {open && (
        <div className="absolute right-0 top-full z-40 mt-1 w-60 rounded-xl border border-line bg-elevated p-2 shadow-lg shadow-black/40">
          <p className="px-2 py-1 text-xs text-muted">Signed in as</p>
          <p className="flex items-center gap-2 px-2 pb-2 text-sm font-medium text-fg">
            <span className="truncate">{user.username}</span> <StatusBadge tone="brand">{user.role}</StatusBadge>
          </p>
          <button type="button" onClick={onLogout} className="w-full rounded-lg px-2 py-2 text-left text-sm text-fg hover:bg-hover">
            Log out
          </button>
        </div>
      )}
    </div>
  )
}

export default function AppHeader() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [openedOn, setOpenedOn] = useState(null)
  const open = openedOn === pathname

  const isStaff = user.role === 'TEACHER' || user.role === 'ADMIN'
  // Navigation convenience only: the backend independently rejects unauthorized callers with 403.
  const manage = [
    ...(isStaff ? [{ to: '/teacher/questions', label: 'Questions', hint: 'Create and edit challenge questions' }] : []),
    ...(user.role === 'ADMIN' ? [{ to: '/admin/users', label: 'Users', hint: 'Roles and account status' }] : []),
  ]

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const mobileLink = ({ isActive }) =>
    `block rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-accent-soft font-medium text-accent' : 'text-fg-2 hover:bg-hover hover:text-fg'}`

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-sidebar/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Link to="/" className="flex items-center gap-2 text-lg font-bold text-fg">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent text-sm text-on-accent" aria-hidden="true">
              S
            </span>
            SpringLab
          </Link>
          <nav aria-label="Main" className="hidden items-center lg:flex">
            <NavLink to="/" end className={linkClass}>
              Dashboard
            </NavLink>
            <NavMenu label="Learn" items={LEARN} />
            <NavLink to="/arena" className={linkClass}>
              Challenge Arena
            </NavLink>
            <NavLink to="/progress" className={linkClass}>
              Progress
            </NavLink>
            {manage.length === 1 && (
              <NavLink to={manage[0].to} className={linkClass}>
                {manage[0].label}
              </NavLink>
            )}
            {manage.length > 1 && <NavMenu label="Manage" items={manage} />}
          </nav>
        </div>
        <div className="hidden lg:block">
          <AccountMenu user={user} onLogout={handleLogout} />
        </div>
        <button
          type="button"
          className="rounded-lg border border-line p-2 text-fg hover:bg-hover lg:hidden"
          aria-expanded={open}
          aria-controls="mobile-nav"
          aria-label={open ? 'Close menu' : 'Open menu'}
          onClick={() => setOpenedOn(open ? null : pathname)}
        >
          <Icon name={open ? 'close' : 'menu'} />
        </button>
      </div>
      {open && (
        <div id="mobile-nav" className="max-h-[calc(100vh-3.5rem)] overflow-y-auto border-t border-line bg-sidebar px-4 pb-4 pt-2 lg:hidden">
          <nav aria-label="Main (mobile)" className="space-y-3">
            <div className="space-y-1">
              <NavLink to="/" end className={mobileLink}>
                Dashboard
              </NavLink>
              <NavLink to="/arena" className={mobileLink}>
                Challenge Arena
              </NavLink>
              <NavLink to="/progress" className={mobileLink}>
                Progress
              </NavLink>
            </div>
            <div>
              <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted">Learn</p>
              <div className="space-y-1">
                {LEARN.map((item) => (
                  <NavLink key={item.to} to={item.to} className={mobileLink}>
                    {item.label}
                  </NavLink>
                ))}
              </div>
            </div>
            {manage.length > 0 && (
              <div>
                <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted">Manage</p>
                <div className="space-y-1">
                  {manage.map((item) => (
                    <NavLink key={item.to} to={item.to} className={mobileLink}>
                      {item.label}
                    </NavLink>
                  ))}
                </div>
              </div>
            )}
          </nav>
          <div className="mt-4 flex items-center justify-between gap-3 border-t border-line pt-3">
            <span className="flex items-center gap-2 text-sm text-fg">
              {user.username} <StatusBadge tone="brand">{user.role}</StatusBadge>
            </span>
            <button type="button" onClick={handleLogout} className="rounded-lg border border-line-strong px-3 py-1.5 text-sm text-fg hover:bg-hover">
              Log out
            </button>
          </div>
        </div>
      )}
    </header>
  )
}
