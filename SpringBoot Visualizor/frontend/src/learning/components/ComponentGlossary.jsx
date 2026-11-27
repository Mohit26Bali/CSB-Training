import { useState } from 'react'
import { factText } from '../schema/componentFacts'
import CodeInspector from './CodeInspector'

const USED_BY = {
  login: { label: 'LOGIN only', hint: 'Used when someone logs in with a password; a bearer request never uses it' },
  bearer: { label: 'BEARER requests', hint: 'Used to authenticate or authorize requests that carry a token' },
  both: { label: 'LOGIN and BEARER', hint: 'Takes part in both flows' },
}
const EVIDENCE = {
  tested: 'SpringLab statement verified by an automated test',
  documented: 'Framework behaviour as documented by Spring Security; not separately observed here',
}

/**
 * What each Spring Security component does and how SpringLab actually uses it. Each entry says whether the SpringLab
 * statement is verified by a test or is documented framework behaviour, and shows a value read live from the running
 * application where one exists.
 */
export default function ComponentGlossary({ glossary, facts, factsError, codeRefs }) {
  const [openId, setOpenId] = useState(null)
  return (
    <section aria-label="Spring Security components" className="space-y-2">
      {factsError && <p className="text-sm text-error">Could not read the running configuration: {factsError}</p>}
      {glossary.map((entry) => {
        const live = factText(facts, entry.factKey)
        const flow = USED_BY[entry.usedBy]
        return (
          <article key={entry.id} className="rounded-lg border border-line bg-surface p-3 shadow-sm" data-testid={`component-${entry.id}`}>
            <header className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">{entry.name}</h3>
              <span className="rounded border border-line-strong bg-elevated px-2 py-0.5 text-xs" title={flow.hint}>
                {flow.label}
              </span>
            </header>
            <p className="mt-1 text-sm text-fg-2">
              <span className="font-medium">What it does: </span>
              {entry.role}
            </p>
            <p className="mt-1 text-sm text-fg-2">
              <span className="font-medium">In SpringLab: </span>
              {entry.inSpringLab}
            </p>
            {entry.factKey && (
              <p className="mt-1 rounded bg-success-soft p-2 text-xs text-success" data-testid={`fact-${entry.id}`}>
                <span className="font-semibold">● Read from the running application: </span>
                {live ?? (facts ? '(not reported)' : 'loading…')}
              </p>
            )}
            <p className="mt-1 text-xs text-muted">{EVIDENCE[entry.evidence]}</p>
            {entry.codeRefId && (
              <>
                <button
                  type="button"
                  onClick={() => setOpenId(openId === entry.id ? null : entry.id)}
                  aria-expanded={openId === entry.id}
                  className="mt-1 text-sm text-accent hover:underline"
                >
                  {openId === entry.id ? 'Hide the code' : 'See the code'}
                </button>
                {openId === entry.id && (
                  <div className="mt-2">
                    <CodeInspector reference={codeRefs?.[entry.codeRefId]} />
                  </div>
                )}
              </>
            )}
          </article>
        )
      })}
    </section>
  )
}
