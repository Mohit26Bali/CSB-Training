const palettes = [
  'from-amber-600 to-orange-800',
  'from-emerald-600 to-teal-800',
  'from-sky-600 to-indigo-800',
  'from-rose-600 to-red-800',
  'from-violet-600 to-purple-800',
  'from-slate-600 to-slate-800',
]

const hash = (text: string) => [...text].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)

/** A generated cover: coloured by category, with the title and author set on it like a real jacket. */
export function BookCover({ title, author, category, className = '' }: { title: string; author: string; category: string; className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`relative flex flex-col justify-between overflow-hidden bg-linear-to-br p-3 text-white ${palettes[hash(category) % palettes.length]} ${className}`}
    >
      <span className="absolute inset-y-0 left-0 w-2 bg-black/20" />
      <p className="pl-2 font-serif text-sm font-bold leading-tight line-clamp-4">{title}</p>
      <p className="pl-2 text-[10px] uppercase tracking-wide opacity-80 line-clamp-2">{author}</p>
    </div>
  )
}

/** A wide photo with a heading over it, used at the top of the member pages. */
export function Banner({ src, title, text }: { src: string; title: string; text: string }) {
  return (
    <div className="relative mb-6 overflow-hidden rounded-2xl">
      <img src={src} alt="" className="h-40 w-full object-cover sm:h-48" />
      <div className="absolute inset-0 flex flex-col justify-end bg-linear-to-t from-black/70 via-black/20 to-transparent p-5 text-white">
        <h1 className="text-2xl font-semibold">{title}</h1>
        <p className="text-sm opacity-90">{text}</p>
      </div>
    </div>
  )
}
