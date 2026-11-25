import { Link } from 'react-router-dom'
import { buttonClass } from '../lib/buttonStyles'

/** Shown for any URL no route matches (see App.tsx). */
export function NotFound() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center px-4 pb-10 pt-32 text-center">
      <p className="text-7xl font-extrabold tracking-tight text-ember-500">404</p>
      <h1 className="mt-3 text-2xl font-bold text-ink">This scene was cut</h1>
      <p className="mt-2 text-muted">The page you are looking for doesn’t exist or has moved.</p>
      <Link to="/" className={buttonClass('primary', 'mt-8')}>
        Back to the home page
      </Link>
    </div>
  )
}
