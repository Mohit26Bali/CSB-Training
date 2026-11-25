/**
 * The bank's logo mark and wordmark. Used by components/AppShell (sidebar), components/AuthShell (login / register
 * panels) and pages/NotFound. The mark matches public/favicon.svg.
 */
export function LogoMark({ className = 'size-9' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="#0f2557" />
      <path d="M43 23A14 14 0 1 0 43 41" fill="none" stroke="#fff" strokeWidth="7.5" strokeLinecap="round" />
      <circle cx="49" cy="14.5" r="5" fill="#22c28f" />
    </svg>
  )
}

export function Brand({ light = false }: { light?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <LogoMark />
      <div className="leading-tight">
        <p className={`text-[15px] font-semibold tracking-tight ${light ? 'text-white' : 'text-ink'}`}>Crestline Bank</p>
        <p className={`text-[11px] font-medium uppercase tracking-[0.14em] ${light ? 'text-white/60' : 'text-faint'}`}>
          Digital banking
        </p>
      </div>
    </div>
  )
}
