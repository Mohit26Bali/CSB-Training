import type { StatementResult } from '../api'
import { ResultTable } from './ResultTable'

/** Shows whatever a statement produced: rows, a row-change summary, an error, or a note about transaction control. */
export function ResultView({ result, caption }: { result: StatementResult & { ms?: number }; caption?: string }) {
  switch (result.kind) {
    case 'rows':
      return (
        <div>
          <p className="meta" role="status">
            {result.totalRows} {result.totalRows === 1 ? 'row' : 'rows'}
            {result.truncated && ` (showing the first ${result.rows.length})`}
            {result.ms !== undefined && ` · ${result.ms} ms`}
          </p>
          <ResultTable columns={result.columns} rows={result.rows} caption={caption} />
        </div>
      )
    case 'change':
      return (
        <p className="notice ok" role="status">
          {result.affectedRows} {result.affectedRows === 1 ? 'row' : 'rows'} affected
          {result.changedRows !== null && result.changedRows !== result.affectedRows ? ` (${result.changedRows} actually changed)` : ''}
          {result.insertId ? ` · new id ${result.insertId}` : ''}
        </p>
      )
    case 'error':
      return (
        <p className="notice error" role="alert">
          <strong>{result.error.code ? `Error ${result.error.code}` : 'Error'}</strong> {result.error.message}
        </p>
      )
    case 'transaction':
      return <p className="notice info">{result.note}</p>
    case 'setting':
      return <p className="notice info">Session setting applied.</p>
  }
}
