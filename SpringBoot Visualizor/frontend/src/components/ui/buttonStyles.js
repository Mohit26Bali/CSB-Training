const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md border font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-app disabled:cursor-not-allowed disabled:opacity-45 disabled:saturate-50'
const VARIANTS = {
  primary: 'border-accent bg-accent text-on-accent hover:border-accent-hover hover:bg-accent-hover active:border-accent-active active:bg-accent-active',
  secondary: 'border-line-strong bg-surface text-fg hover:border-accent-2 hover:bg-elevated',
  ghost: 'border-transparent bg-transparent text-fg-2 hover:bg-hover hover:text-fg',
  danger: 'border-error bg-error text-on-accent hover:brightness-110 active:brightness-95',
}
const SIZES = { sm: 'px-3 py-1.5 text-sm', md: 'px-4 py-2 text-sm' }

/** The single source of button styling; Button and ButtonLink both use it so a link can look like a button. */
export function buttonClass({ variant = 'primary', size = 'md', className = '' } = {}) {
  return `${BASE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`.trim()
}
