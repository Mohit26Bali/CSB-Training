import { useState } from 'react'
import { api, type AssignmentDetail } from '../api'
import { useAsync } from '../hooks'
import { ErrorNotice, Loading } from '../components/Feedback'
import { ResultTable } from '../components/ResultTable'

export function DataTab({ a }: { a: AssignmentDetail }) {
  const [table, setTable] = useState(a.tables[0]?.name ?? '')
  const [page, setPage] = useState(1)
  const data = useAsync(() => api.table(a.id, table, page), [a.id, table, page])

  return (
    <>
      <div className="toolbar">
        <label>
          Table{' '}
          <select
            value={table}
            onChange={(e) => {
              setTable(e.target.value)
              setPage(1)
            }}
          >
            {a.tables.map((t) => (
              <option key={t.name} value={t.name}>
                {t.name} ({t.rowCount})
              </option>
            ))}
          </select>
        </label>
      </div>
      {data.loading && !data.data && <Loading label="Loading rows" />}
      {data.error && <ErrorNotice message={data.error} onRetry={data.reload} />}
      {data.data && (
        <>
          <ResultTable columns={data.data.columns} rows={data.data.rows} caption={`Rows of ${data.data.table}`} />
          <div className="pager">
            <button className="btn small" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span role="status">
              Page {data.data.page} of {data.data.totalPages} · {data.data.totalRows} rows
            </span>
            <button className="btn small" disabled={page >= data.data.totalPages} onClick={() => setPage(page + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </>
  )
}
