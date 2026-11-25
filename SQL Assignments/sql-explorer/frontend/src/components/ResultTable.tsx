import type { Cell } from '../api'

function show(cell: Cell) {
  if (cell === null) return <span className="null">NULL</span>
  return String(cell)
}

const NUMERIC = /^-?\d+(\.\d+)?$/

/** A result grid. Values are shown as text (never as HTML). */
export function ResultTable({ columns, rows, caption }: { columns: string[]; rows: Cell[][]; caption?: string }) {
  if (columns.length === 0) return null
  return (
    <div className="table-wrap" role="region" aria-label={caption ?? 'Results'} tabIndex={0}>
      <table>
        {caption && <caption className="visually-hidden">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c, i) => (
              <th key={`${c}-${i}`} scope="col">{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td className="empty" colSpan={columns.length}>No rows.</td>
            </tr>
          ) : (
            rows.map((row, r) => (
              <tr key={r}>
                {row.map((cell, c) => (
                  <td key={c} className={typeof cell === 'number' || (typeof cell === 'string' && NUMERIC.test(cell)) ? 'num' : undefined} title={typeof cell === 'string' && cell.length > 40 ? cell : undefined}>
                    {show(cell)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
