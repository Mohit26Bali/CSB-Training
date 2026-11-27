import { Link } from 'react-router-dom'
import { HERO_HOME } from '../assets/heroImages'

/** The sign-in and registration frame: a photographic panel (large screens only) beside the form. */
export default function AuthLayout({ children }) {
  return (
    <main className="grid min-h-screen bg-app lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden lg:block" aria-hidden="true">
        <img src={HERO_HOME.src} alt="" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-app via-app/70 to-app/40" />
        <div className="relative flex h-full flex-col justify-end p-10">
          <p className="text-3xl font-bold leading-tight text-fg">See Spring Boot run, not just read about it.</p>
          <p className="mt-3 max-w-md text-fg-2">Follow a real request through security, MVC and Hibernate, and know which parts were observed and which were simulated.</p>
          <p className="mt-8 text-xs text-muted">{HERO_HOME.credit}</p>
        </div>
      </aside>
      <div className="flex flex-col items-center justify-center gap-6 p-6">
        <Link to="/welcome" className="flex items-center gap-2 text-lg font-bold text-fg">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent text-sm text-on-accent" aria-hidden="true">S</span>
          SpringLab
        </Link>
        {children}
      </div>
    </main>
  )
}
