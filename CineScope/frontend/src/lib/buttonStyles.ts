/**
 * Class strings for the app's buttons, shared by components/ui.tsx (the Button component) and by places that need the
 * same look on a router link or a plain element (Hero, TitlePage, NotFound). Kept out of ui.tsx so that file only
 * exports components.
 */
export type ButtonVariant = 'primary' | 'secondary' | 'glass' | 'ghost' | 'danger'

export const buttonVariants: Record<ButtonVariant, string> = {
  primary: 'bg-ember-500 text-white shadow-lg shadow-ember-500/25 hover:bg-ember-600 disabled:bg-ember-500/40',
  secondary: 'border border-line bg-surface text-ink hover:border-ember-400/60 disabled:text-faint',
  glass: 'border border-white/25 bg-white/12 text-white backdrop-blur-md hover:bg-white/22 disabled:text-white/50',
  ghost: 'text-muted hover:bg-ink/10 hover:text-ink disabled:text-faint',
  danger: 'bg-rose-600 text-white shadow-lg shadow-rose-600/25 hover:bg-rose-700 disabled:bg-rose-600/40',
}

export function buttonClass(variant: ButtonVariant = 'primary', extra = ''): string {
  return `inline-flex items-center justify-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-all ${buttonVariants[variant]} ${extra}`
}
