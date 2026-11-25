/**
 * The CineScope logo mark (a film-lens ring with a play triangle) and wordmark. Used by components/AppShell. The mark
 * matches public/favicon.svg.
 */
export function LogoMark({ className = 'size-9' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="cine-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#ff7a59" />
          <stop offset="1" stopColor="#e0314b" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill="#0a0b10" />
      <circle cx="32" cy="32" r="19" fill="none" stroke="url(#cine-g)" strokeWidth="5" />
      <path d="M27 23.5v17l14-8.5z" fill="#fff" />
    </svg>
  )
}

export function Brand() {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      <span className="text-lg font-extrabold tracking-tight text-ink">
        Cine<span className="text-ember-500">Scope</span>
      </span>
    </span>
  )
}
