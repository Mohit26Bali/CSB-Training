import { useState } from 'react'
import { describeError } from '../../api/errors'
import { inspectToken, issueToken } from '../api/labJwtApi'
import JwtAnatomy from './JwtAnatomy'

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN']

/**
 * Hands-on token anatomy. Issue a LAB token of a chosen kind for yourself, or paste any token to see it decoded and
 * checked against the lab key. Pasting your real application token is safe: it is shown as decoded and fails
 * verification, because the lab key is not the application's key.
 */
export default function TokenWorkshop({ variants }) {
  const issuable = variants.filter((v) => v.issued)
  const [variantId, setVariantId] = useState('VALID')
  const [claimedRole, setClaimedRole] = useState('')
  const [pasted, setPasted] = useState('')
  const [result, setResult] = useState(null) // { token|null, inspection }
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function run(action) {
    setBusy(true)
    setError(null)
    try {
      setResult(await action())
    } catch (err) {
      setError(err.response ? describeError(err).message : err.message)
    } finally {
      setBusy(false)
    }
  }

  const issue = () =>
    run(async () => {
      const issued = await issueToken(variantId, claimedRole)
      return { token: issued.token, inspection: issued.inspection }
    })
  const inspect = () => run(async () => ({ token: pasted.trim(), inspection: await inspectToken(pasted.trim()) }))

  const selected = issuable.find((v) => v.id === variantId)
  return (
    <div className="space-y-3">
      <div className="grid gap-3 md:grid-cols-2">
        <section className="space-y-2 rounded-lg border border-line bg-surface p-3 shadow-sm" aria-label="Issue a lab token">
          <h3 className="text-sm font-semibold">Issue a lab token for yourself</h3>
          <label className="block text-sm">
            Kind of token
            <select
              value={variantId}
              onChange={(e) => setVariantId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-line-strong px-2 py-1.5.5"
            >
              {issuable.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.title}
                </option>
              ))}
            </select>
          </label>
          {selected && <p className="text-xs text-fg-2">{selected.description}</p>}
          <label className="block text-sm">
            Role the token claims
            <select
              value={claimedRole}
              onChange={(e) => setClaimedRole(e.target.value)}
              className="mt-1 block w-full rounded-md border border-line-strong px-2 py-1.5.5"
            >
              <option value="">My real role</option>
              {ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={issue}
            disabled={busy}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-on-accent shadow-sm transition-colors hover:bg-accent-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {busy ? 'Working…' : 'Issue and inspect'}
          </button>
        </section>

        <section className="space-y-2 rounded-lg border border-line bg-surface p-3 shadow-sm" aria-label="Inspect a pasted token">
          <h3 className="text-sm font-semibold">Or paste any token</h3>
          <label className="block text-sm">
            Token
            <textarea
              value={pasted}
              onChange={(e) => setPasted(e.target.value)}
              rows={4}
              maxLength={4096}
              spellCheck={false}
              className="mt-1 block w-full rounded-md border border-line-strong px-2 py-1.5.5 font-mono text-xs"
              placeholder="header.payload.signature"
            />
          </label>
          <button
            type="button"
            onClick={inspect}
            disabled={busy || pasted.trim().length === 0}
            className="rounded border border-line-strong bg-surface px-3 py-1.5 text-sm font-medium hover:bg-subtle disabled:opacity-60"
          >
            Inspect
          </button>
          <p className="text-xs text-muted">
            The text is decoded and checked on the server and is not stored. A real SpringLab token will decode but fail
            verification here, because it is signed with a different key.
          </p>
        </section>
      </div>

      {error && (
        <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
          {error}
        </p>
      )}
      {result && <JwtAnatomy inspection={result.inspection} token={result.token} />}
    </div>
  )
}
