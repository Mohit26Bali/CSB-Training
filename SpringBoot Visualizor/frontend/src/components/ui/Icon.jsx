const PATHS = {
  shield: 'M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6l7-3zM9 12l2 2 4-4',
  route: 'M6 19a2 2 0 100-4 2 2 0 000 4zM18 9a2 2 0 100-4 2 2 0 000 4zM8 17h6a4 4 0 004-4v-2M6 15V9a4 4 0 014-4h2',
  database: 'M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6',
  trophy: 'M8 4h8v5a4 4 0 01-8 0V4zM8 6H5a2 2 0 002 4M16 6h3a2 2 0 01-2 4M12 13v4M9 21h6M10 17h4',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  book: 'M4 5a2 2 0 012-2h13v16H6a2 2 0 00-2 2V5zM4 19a2 2 0 012-2h13',
  check: 'M5 12l5 5 9-10',
  clock: 'M12 7v5l3 2M12 21a9 9 0 100-18 9 9 0 000 18z',
  lock: 'M7 11V8a5 5 0 0110 0v3M6 11h12v9H6z',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  menu: 'M4 7h16M4 12h16M4 17h16',
  close: 'M6 6l12 12M18 6L6 18',
  chevron: 'M6 9l6 6 6-6',
  users: 'M16 19v-1a4 4 0 00-4-4H8a4 4 0 00-4 4v1M10 11a3 3 0 100-6 3 3 0 000 6zM20 19v-1a3 3 0 00-2-2.8M16 5.2a3 3 0 010 5.6',
  edit: 'M4 20h4L19 9a2.8 2.8 0 00-4-4L4 16v4z',
  alert: 'M12 9v4M12 17h.01M10.3 4l-8 14a2 2 0 001.7 3h16a2 2 0 001.7-3l-8-14a2 2 0 00-3.4 0z',
}

/** Inline stroke icon (no icon library). Decorative unless a `label` is given. */
export default function Icon({ name, className = 'h-5 w-5', label }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`shrink-0 ${className}`}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <path d={PATHS[name] ?? PATHS.book} />
    </svg>
  )
}
