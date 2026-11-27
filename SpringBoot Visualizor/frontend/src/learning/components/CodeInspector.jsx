import { tokenizeLine } from '../schema/codeHighlight'

const TOKEN_CLASS = {
  annotation: 'text-warning font-semibold',
  keyword: 'text-layer-security',
  string: 'text-success',
  comment: 'text-muted italic',
  plain: 'text-fg',
}

/**
 * Shows an excerpt of REAL SpringLab source (the backend's tests verify the excerpt equals the file). Lines that have
 * an explanatory note are highlighted. When a step has no verified source (framework internals) it says so instead of
 * showing invented code.
 */
export default function CodeInspector({ reference }) {
  if (!reference) {
    return (
      <div className="rounded-lg border border-line bg-elevated p-4 text-sm text-fg-2">
        No SpringLab source is associated with this step. It describes framework behaviour whose code is not part of
        this application, so none is shown rather than an invented excerpt.
      </div>
    )
  }
  const lines = reference.excerpt.split('\n')
  const notesByLine = new Map(reference.notes.map((n) => [n.line, n]))
  return (
    <figure className="overflow-hidden rounded-lg border border-line bg-code shadow-sm" aria-label={`Code: ${reference.title}`}>
      <figcaption className="border-b border-line bg-code-header px-3 py-2 text-sm">
        <span className="font-semibold text-fg">{reference.title}</span>
        <span className="block break-all font-mono text-xs text-muted">
          {reference.file} - lines {reference.startLine}-{reference.endLine}
        </span>
      </figcaption>
      {/* Long lines scroll sideways inside the box; the line numbers stay pinned so a scrolled line is still identifiable.
          The box never grows the page: min-w-0 on the grid cells and overflow-auto here keep scrolling local. */}
      <pre className="max-h-112 overflow-auto p-0 text-xs leading-5" tabIndex={0}>
        <code className="block w-max min-w-full">
          {lines.map((text, i) => {
            const lineNo = reference.startLine + i
            const noted = notesByLine.has(lineNo)
            return (
              <div key={lineNo} className={noted ? 'border-l-4 border-warning bg-code-header' : 'border-l-4 border-transparent'}>
                <span
                  className={`sticky left-0 mr-2 inline-block w-10 select-none pr-2 text-right text-muted ${
                    noted ? 'bg-code-header' : 'bg-code'
                  }`}
                >
                  {lineNo}
                </span>
                {tokenizeLine(text, reference.language).map((t, j) => (
                  <span key={j} className={TOKEN_CLASS[t.type]}>
                    {t.text}
                  </span>
                ))}
              </div>
            )
          })}
        </code>
      </pre>
      <ul className="space-y-1.5 border-t border-line bg-elevated p-3 text-sm text-fg-2">
        {reference.notes.map((n) => (
          <li key={`${n.line}-${n.token}`} className="break-words">
            <span className="font-mono text-xs text-muted">line {n.line}</span>{' '}
            <code className="rounded bg-warning-soft px-1 text-xs text-warning">{n.token}</code> {n.text}
          </li>
        ))}
      </ul>
    </figure>
  )
}
