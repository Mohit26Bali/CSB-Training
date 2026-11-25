import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAsync } from '../hooks'
import { ErrorNotice, Loading } from '../components/Feedback'

export function HomePage() {
  const list = useAsync(() => api.assignments(), [])
  return (
    <>
      <h1>SQL Explorer</h1>
      <p className="lead">
        Each card is one of your SQL assignments, loaded with its own seed data. Open one to see its tables, browse the rows, run its queries,
        watch what its INSERT / UPDATE / DELETE scripts do (nothing is ever saved), and try the negative tests that are meant to fail.
      </p>
      {list.loading && <Loading label="Loading assignments" />}
      {list.error && <ErrorNotice message={list.error} onRetry={list.reload} />}
      {list.data && (
        <ul className="cards">
          {list.data.map((a) => (
            <li key={a.id}>
              <Link to={`/a/${a.id}`} className="card">
                <span className="card-top">
                  <span className="num-badge">{a.id}</span>
                  <span className={`pill cat-${a.category.toLowerCase()}`}>{a.category}</span>
                </span>
                <strong>{a.title.replace(/\s*\((DDL|DML|SELECT)\)$/, '')}</strong>
                <span className="meta">database <code>{a.originalDb}</code></span>
                <dl className="stats">
                  <div><dt>Tables</dt><dd>{a.tables}</dd></div>
                  <div><dt>Rows</dt><dd>{a.rows}</dd></div>
                  <div><dt>Queries</dt><dd>{a.queries}</dd></div>
                  <div><dt>Must fail</dt><dd>{a.negatives}</dd></div>
                </dl>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
