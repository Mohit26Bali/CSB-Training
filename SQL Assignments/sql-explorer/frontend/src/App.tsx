import { useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router-dom'
import { api } from './api'
import { useAsync } from './hooks'
import { HomePage } from './pages/HomePage'
import { AssignmentPage } from './pages/AssignmentPage'

const GROUPS: { category: string; label: string }[] = [
  { category: 'DDL', label: 'DDL: creating schemas' },
  { category: 'DML', label: 'DML: changing data' },
  { category: 'SELECT', label: 'SELECT: querying' },
  { category: 'Capstone', label: 'Capstone' },
]

function Sidebar() {
  const list = useAsync(() => api.assignments(), [])
  // open on a wide screen, collapsed behind a button on a phone (it would otherwise fill the first screen)
  const [open, setOpen] = useState(() => typeof window.matchMedia !== 'function' || window.matchMedia('(min-width: 861px)').matches)
  return (
    <nav className="sidebar" aria-label="Assignments">
      <details open={open} onToggle={(e) => setOpen(e.currentTarget.open)}>
        <summary>Assignments</summary>
      {list.loading && <p className="meta">Loading…</p>}
      {list.error && <p className="notice error">{list.error}</p>}
      {list.data &&
        GROUPS.map((g) => {
          const items = list.data!.filter((a) => a.category === g.category)
          if (items.length === 0) return null
          return (
            <div key={g.category} className="nav-group">
              <h2>{g.label}</h2>
              <ul>
                {items.map((a) => (
                  <li key={a.id}>
                    <NavLink to={`/a/${a.id}`} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                      <span className="num-badge">{a.id}</span>
                      <span>{a.title.replace(/\s*\((DDL|DML|SELECT)\)$/, '')}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          )
        })}
      </details>
    </nav>
  )
}

export function App() {
  return (
    <div className="shell">
      <a className="skip" href="#main">Skip to content</a>
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="logo" aria-hidden="true">⛁</span> SQL Explorer
        </Link>
        <span className="tagline">The 15 SQL assignments, browsable and runnable</span>
      </header>
      <Sidebar />
      <main id="main" className="content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/a/:id" element={<AssignmentPage />} />
          <Route path="*" element={<p className="notice error">That page does not exist. <Link to="/">Back to the start</Link></p>} />
        </Routes>
      </main>
    </div>
  )
}
