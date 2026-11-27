import { Fragment } from 'react'
import { parseBlocks } from './markdown'

/**
 * Renders the small subset of Markdown the problem statements use: ## and ### headings, paragraphs, bullet and numbered lists (one line per
 * item), fenced code blocks, and inline `code`, **bold** and *italic*. It builds React elements and never sets HTML, so text from the server
 * cannot inject markup. Anything it does not understand is shown as plain text.
 */
const INLINE = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*\s][^*]*\*)/g

function inline(text) {
  return text.split(INLINE).map((part, i) => {
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return (
        <code key={i} className="rounded bg-code px-1 py-0.5 font-mono text-[0.85em] text-fg">
          {part.slice(1, -1)}
        </code>
      )
    }
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return <strong key={i} className="font-semibold text-fg">{part.slice(2, -2)}</strong>
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) return <em key={i}>{part.slice(1, -1)}</em>
    return <Fragment key={i}>{part}</Fragment>
  })
}

export default function SimpleMarkdown({ text, className = '' }) {
  return (
    <div className={`space-y-3 text-sm leading-relaxed text-fg-2 ${className}`}>
      {parseBlocks(text).map((b, i) => {
        switch (b.type) {
          case 'h3':
            return <h3 key={i} className="pt-2 text-base font-semibold text-fg">{inline(b.text)}</h3>
          case 'h4':
            return <h4 key={i} className="pt-1 text-sm font-semibold text-fg">{inline(b.text)}</h4>
          case 'ul':
            return <ul key={i} className="list-disc space-y-1 pl-5">{b.items.map((t, j) => <li key={j}>{inline(t)}</li>)}</ul>
          case 'ol':
            return <ol key={i} className="list-decimal space-y-1.5 pl-5">{b.items.map((t, j) => <li key={j}>{inline(t)}</li>)}</ol>
          case 'code':
            return <pre key={i} tabIndex={0} className="overflow-x-auto rounded-md border border-line-subtle bg-code p-3 font-mono text-xs leading-relaxed text-fg"><code>{b.text}</code></pre>
          default:
            return <p key={i}>{inline(b.text)}</p>
        }
      })}
    </div>
  )
}
