/**
 * A circular score badge: the ring fills in proportion to the TMDB score (0-10) and is green for 7 and above, amber for
 * 5 and above, red below. Used on the detail page next to the vote count.
 */
export function RatingRing({ rating, size = 64 }: { rating: number; size?: number }) {
  const percent = Math.max(0, Math.min(100, rating * 10))
  const radius = 26
  const circumference = 2 * Math.PI * radius
  const color = rating >= 7 ? '#22c55e' : rating >= 5 ? '#eab308' : '#ef4444'
  return (
    <div role="img" aria-label={rating > 0 ? `User score ${rating.toFixed(1)} out of 10` : 'Not rated yet'} className="relative grid shrink-0 place-items-center" style={{ width: size, height: size }}>
      <svg viewBox="0 0 64 64" className="absolute inset-0 -rotate-90">
        <circle cx="32" cy="32" r={radius} fill="rgb(0 0 0 / 0.55)" stroke="rgb(255 255 255 / 0.15)" strokeWidth="5" />
        {rating > 0 && (
          <circle
            cx="32"
            cy="32"
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth="5"
            strokeLinecap="round"
            strokeDasharray={`${(percent / 100) * circumference} ${circumference}`}
          />
        )}
      </svg>
      <span className="relative text-sm font-extrabold text-white">{rating > 0 ? rating.toFixed(1) : 'NR'}</span>
    </div>
  )
}
