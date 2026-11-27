import Icon from './Icon'

/**
 * Progressive disclosure: a titled section that is closed until the learner opens it. Built on the native <details> element,
 * so it works with the keyboard and with screen readers without any script, and its content stays in the page (the state of
 * what is inside is not lost when it is closed).
 */
export default function Disclosure({ title, meta, defaultOpen = false, children, className = '' }) {
  return (
    <details open={defaultOpen} className={`group rounded-xl border border-line bg-surface ${className}`}>
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 rounded-xl px-4 py-3 text-sm font-medium text-fg hover:bg-elevated [&::-webkit-details-marker]:hidden">
        <span>{title}</span>
        <span className="flex min-w-0 items-center gap-2 text-xs font-normal text-muted">
          {meta && <span className="truncate">{meta}</span>}
          <Icon name="chevron" className="h-4 w-4 transition-transform group-open:rotate-180 motion-reduce:transition-none" />
        </span>
      </summary>
      <div className="border-t border-line p-3">{children}</div>
    </details>
  )
}
