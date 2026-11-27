import { parseDay } from '../dates'

/** A little calendar tile: month, day number and weekday. */
export function DateBlock({ start, muted }: { start: string; muted?: boolean }) {
  const d = parseDay(start)
  return (
    <div className={`flex w-16 shrink-0 flex-col items-center justify-center rounded-lg px-2 py-2 text-center ${muted ? 'bg-slate-100 text-slate-500' : 'bg-brand-600 text-white'}`}>
      <span className="text-[11px] font-medium uppercase">{new Intl.DateTimeFormat(undefined, { month: 'short' }).format(d)}</span>
      <span className="text-2xl font-semibold leading-none">{d.getDate()}</span>
      <span className="text-[11px]">{new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(d)}</span>
    </div>
  )
}
