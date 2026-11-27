/** Eligibility score 0 to 100 with a plain-language hint, so colour is never the only signal. */
export function ScoreBadge({ score, meets }: { score: number; meets: boolean }) {
  const tone = meets
    ? 'bg-emerald-100 text-emerald-800'
    : 'bg-amber-100 text-amber-800'
  return (
    <span className="inline-flex items-center gap-2 text-xs">
      <span className={`rounded-full px-2.5 py-0.5 font-semibold tabular-nums ${tone}`}>Score {score}</span>
      <span className="text-slate-500">{meets ? 'Meets criteria' : 'Does not meet criteria'}</span>
    </span>
  )
}
