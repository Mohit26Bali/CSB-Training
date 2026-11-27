import { useState } from 'react'

const gradients = ['from-purple-600 to-indigo-800', 'from-fuchsia-600 to-purple-800', 'from-violet-600 to-slate-800', 'from-indigo-600 to-purple-900']

function initials(name: string): string {
  return name
    .replace(/^Dr\.?\s+/i, '')
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('')
}

/** A round picture for the provider's speciality (/providers/<speciality>.jpg), or their initials on a coloured disc. */
export function ProviderPhoto({ id, name, title, className = 'size-14' }: { id: number; name: string; title: string; className?: string }) {
  const [failed, setFailed] = useState(false)
  const slug = title.toLowerCase().replace(/[^a-z]+/g, '-').replace(/^-|-$/g, '')
  if (!failed) {
    return <img src={`/providers/${slug}.jpg`} alt="" loading="lazy" onError={() => setFailed(true)} className={`shrink-0 rounded-full object-cover ring-2 ring-white ${className}`} />
  }
  return (
    <div aria-hidden="true" className={`flex shrink-0 items-center justify-center rounded-full bg-linear-to-br font-semibold text-white ring-2 ring-white ${gradients[id % gradients.length]} ${className}`}>
      {initials(name)}
    </div>
  )
}
