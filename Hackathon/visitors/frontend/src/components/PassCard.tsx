import type { Visit } from '../api/types'
import { formatDay } from './ui'

/** A deterministic pattern of squares from the pass code: it looks like a QR code and is the same every time for one pass. */
function pattern(code: string, size = 11): boolean[][] {
  let seed = [...code].reduce((s, ch) => (s * 31 + ch.charCodeAt(0)) >>> 0, 7)
  const next = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0
    return seed / 4294967296
  }
  const finder = (r: number, c: number) => {
    const inBox = (r0: number, c0: number) => r >= r0 && r < r0 + 3 && c >= c0 && c < c0 + 3
    return inBox(0, 0) || inBox(0, size - 3) || inBox(size - 3, 0)
  }
  return Array.from({ length: size }, (_, r) => Array.from({ length: size }, (_, c) => (finder(r, c) ? !(r % 3 === 1 && c % 3 === 1) : next() > 0.5)))
}

export function QrLike({ code, className = 'size-28' }: { code: string; className?: string }) {
  const cells = pattern(code)
  return (
    <svg viewBox="0 0 11 11" className={`${className} rounded bg-white p-1 text-slate-900`} role="img" aria-label={`Pass pattern for ${code}`} shapeRendering="crispEdges">
      {cells.map((row, r) => row.map((on, c) => (on ? <rect key={`${r}-${c}`} x={c} y={r} width="1" height="1" fill="currentColor" /> : null)))}
    </svg>
  )
}

/** The visitor's pass, ready to show on a phone or print. */
export function PassCard({ visit }: { visit: Visit }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-md">
      <div className="bg-brand-800 px-5 py-3 text-white">
        <p className="text-xs uppercase tracking-widest opacity-80">Visitor pass</p>
        <p className="font-mono text-2xl font-bold tracking-wider">{visit.passCode}</p>
      </div>
      <div className="flex items-center gap-4 p-5">
        <QrLike code={visit.passCode} />
        <div className="min-w-0 text-sm">
          <p className="truncate text-lg font-semibold text-slate-900">{visit.visitorName}</p>
          {visit.company && <p className="truncate text-slate-600">{visit.company}</p>}
          <p className="mt-2 text-slate-700">
            {formatDay(visit.expectedDate)}
            {visit.expectedTime && ` at ${visit.expectedTime.slice(0, 5)}`}
          </p>
          <p className="text-slate-500">Meeting {visit.hostName}</p>
        </div>
      </div>
      <p className="border-t border-dashed border-slate-200 px-5 py-2 text-center text-xs text-slate-500">Show this code at the security desk</p>
    </div>
  )
}
