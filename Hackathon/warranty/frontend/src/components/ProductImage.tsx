import { useState } from 'react'

const gradients = ['from-teal-600 to-emerald-800', 'from-sky-600 to-indigo-800', 'from-violet-600 to-fuchsia-800', 'from-amber-500 to-rose-700']

/** The category photo, or a coloured tile with the first letter when the picture is missing or fails to load. */
export function ProductImage({ category, label, className = '' }: { category: string; label: string; className?: string }) {
  // remember WHICH category failed, so a different product on the same card later still gets its own picture tried
  const [failedFor, setFailedFor] = useState<string | null>(null)
  const failed = failedFor === category

  if (!failed) {
    return (
      <img
        src={`/products/${category.toLowerCase()}.jpg`}
        alt=""
        loading="lazy"
        onError={() => setFailedFor(category)}
        className={`object-cover ${className}`}
      />
    )
  }
  const hash = [...category].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)
  return (
    <div aria-hidden="true" className={`flex items-center justify-center bg-linear-to-br text-3xl font-semibold text-white/90 ${gradients[hash % gradients.length]} ${className}`}>
      {label.charAt(0).toUpperCase()}
    </div>
  )
}
