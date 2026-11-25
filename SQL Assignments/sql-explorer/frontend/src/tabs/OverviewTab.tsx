import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { AssignmentDetail } from '../api'

const KIND_LABEL: Record<string, string> = {
  schema: 'builds the tables',
  seed: 'inserts the data',
  alter: 'changes the tables',
  queries: 'read-only queries',
  dml: 'changes data',
  negative: 'must fail',
  other: 'other',
}

export function OverviewTab({ a }: { a: AssignmentDetail }) {
  return (
    <>
      <div className="callout">
        <strong>How this demo is built.</strong> The database <code>{a.originalDb}</code> was created from the assignment's own scripts:{' '}
        {a.seedFiles.map((f, i) => (
          <span key={f}>
            {i > 0 && ', '}
            <code>{f}</code>
          </span>
        ))}
        . Nothing you do here is saved: scripts that change data run inside a transaction that is rolled back.
        {a.dmlScripts.length > 0 && (
          <>
            {' '}
            The tables show the data <em>before</em> the data-changing scripts ({a.dmlScripts.join(', ')}) run.
          </>
        )}
      </div>
      <h2>Files in this assignment</h2>
      <ul className="filelist">
        {a.scripts.map((s) => (
          <li key={s.file}>
            <code>{s.file}</code> <span className={`pill kind-${s.kind}`}>{KIND_LABEL[s.kind] ?? s.kind}</span>{' '}
            <span className="meta">{s.statements} statements</span>
          </li>
        ))}
      </ul>
      <h2>The assignment's README</h2>
      <article className="markdown">
        {/* react-markdown never renders raw HTML, so the README text cannot inject markup */}
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{a.readme.replace(/^#\s+.*\n+/, '')}</ReactMarkdown>
      </article>
    </>
  )
}
