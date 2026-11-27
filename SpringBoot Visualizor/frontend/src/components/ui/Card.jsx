/** A graphite surface with a subtle border: the one container style used across the application (12px radius). */
export default function Card({ as: Tag = 'div', className = '', padded = true, ...props }) {
  return <Tag className={`rounded-xl border border-line bg-surface ${padded ? 'p-4 sm:p-5' : ''} ${className}`} {...props} />
}
