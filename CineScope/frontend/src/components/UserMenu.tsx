import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { buttonClass } from '../lib/buttonStyles'
import { initials } from '../lib/format'
import { Icon } from './Icon'

/**
 * The account corner of the header (components/AppShell). Signed out: a "Sign in" link. Signed in: a round avatar with
 * the user's initials that opens a small menu with the watchlist and profile links, "Manage accounts" for administrators, and Sign out. Closes on outside click and Escape.
 */
export function UserMenu() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!session) {
    return (
      <Link to="/login" className={buttonClass('secondary', 'h-10 px-4 py-0')}>
        Sign in
      </Link>
    )
  }

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${session.displayName}`}
        className="grid size-10 place-items-center rounded-full bg-ember-500 text-sm font-bold text-white shadow-lg shadow-ember-500/25 hover:bg-ember-600"
      >
        {initials(session.displayName)}
      </button>
      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-56 overflow-hidden rounded-xl border border-line bg-surface shadow-2xl">
          <div className="border-b border-line px-4 py-3">
            <p className="truncate text-sm font-semibold text-ink">{session.displayName}</p>
            <p className="truncate text-xs text-faint">
              @{session.username}
              {session.role === 'ADMIN' && <span className="ml-2 rounded-full bg-ember-500/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-ember-400">Admin</span>}
            </p>
          </div>
          <Link role="menuitem" to="/watchlist" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm text-ink hover:bg-ink/10">
            <Icon name="bookmark" className="size-4" /> My watchlist
          </Link>
          <Link role="menuitem" to="/profile" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm text-ink hover:bg-ink/10">
            <Icon name="user" className="size-4" /> My profile
          </Link>
          {session.role === 'ADMIN' && (
            <Link role="menuitem" to="/admin/users" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm text-ink hover:bg-ink/10">
              <Icon name="filter" className="size-4" /> Manage accounts
            </Link>
          )}
          {session.role === 'ADMIN' && (
            <Link role="menuitem" to="/admin/moderation" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2.5 text-sm text-ink hover:bg-ink/10">
              <Icon name="info" className="size-4" /> Moderation
            </Link>
          )}
          <button
            role="menuitem"
            type="button"
            onClick={() => {
              setOpen(false)
              logout()
              navigate('/')
            }}
            className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm text-ink hover:bg-ink/10"
          >
            <Icon name="logout" className="size-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  )
}
