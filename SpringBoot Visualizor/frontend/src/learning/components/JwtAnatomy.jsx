import ModeBadge from './ModeBadge'

const STANDARD_CLAIMS = {
  iss: 'issuer: who issued the token',
  sub: 'subject: who the token is about',
  exp: 'expiration time (seconds since 1970)',
  nbf: 'not before (seconds since 1970)',
  iat: 'issued at (seconds since 1970)',
  jti: 'unique token id',
  role: 'custom claim: the role the issuer put in the token (informational only in SpringLab)',
  alg: 'signing algorithm',
}

const STATUS_STYLE = {
  PASSED: 'text-success',
  FAILED: 'font-semibold text-error',
  NOT_REACHED: 'text-muted',
}
const STATUS_ICON = { PASSED: '✓', FAILED: '✗', NOT_REACHED: '–' }

function Json({ value, label }) {
  if (!value) return <p className="text-sm text-muted">(not decodable)</p>
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm" aria-label={label}>
      {Object.entries(value).map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="font-mono text-xs text-accent">{k}</dt>
          <dd>
            <span className="font-mono text-xs break-all">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
            {STANDARD_CLAIMS[k] && <span className="block text-xs text-muted">{STANDARD_CLAIMS[k]}</span>}
          </dd>
        </div>
      ))}
    </dl>
  )
}

/** The token drawn as header.payload.signature so the three parts are visible. */
function TokenLine({ token }) {
  const parts = token.split('.')
  // Decoration only: neutral layer colours, never the red/green/amber that mean error, success or inference elsewhere.
  const classes = ['bg-layer-security/15 text-layer-security', 'bg-layer-client/15 text-layer-client', 'bg-layer-database/15 text-layer-database']
  const names = ['header', 'payload', 'signature']
  return (
    <p className="break-all font-mono text-xs leading-6" aria-label="The token with its three parts coloured">
      {parts.map((part, i) => (
        <span key={i}>
          {i > 0 && <span className="text-muted">.</span>}
          <span className={`rounded px-0.5 ${classes[i] ?? ''}`} title={names[i]}>
            {part || '(empty)'}
          </span>
        </span>
      ))}
    </p>
  )
}

/**
 * Shows one token in two clearly separate ways: what DECODING reveals (no key needed) and what VERIFICATION decides
 * (needs the key). `inspection` is the server's answer; `token` is optional (a lab token, never a real one).
 */
export default function JwtAnatomy({ inspection, token }) {
  if (!inspection) return null
  const { verification } = inspection
  return (
    <section aria-label="Token anatomy" className="space-y-3 rounded-lg border border-line bg-surface p-3 shadow-sm">
      {token && (
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">The lab token</h4>
          <TokenLine token={token} />
          <p className="mt-1 text-xs text-muted">
            <span className="rounded bg-layer-security/15 px-1 text-layer-security">header</span>{' '}
            <span className="rounded bg-layer-client/15 px-1 text-layer-client">payload</span>{' '}
            <span className="rounded bg-layer-database/15 px-1 text-layer-database">signature</span> - a lab token only: signed with the lab key, never
            the application's real key.
          </p>
        </div>
      )}

      <div className="grid gap-3 md:grid-cols-2">
        <div data-testid="decode-panel" className="rounded border border-line p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-sm font-semibold">1. Decoding: reading it (no key needed)</h4>
            <ModeBadge provenance="CAPTURED" />
          </div>
          <p className="mt-1 text-xs text-fg-2">
            Base64URL-decoding the first two parts. Anyone can do this to any token, valid or not, so what it shows
            proves nothing.
          </p>
          {inspection.decodeError && <p className="text-sm text-error">{inspection.decodeError}</p>}
          <h5 className="mt-2 text-xs font-semibold text-muted">Header</h5>
          <Json value={inspection.header} label="Decoded header" />
          <h5 className="mt-2 text-xs font-semibold text-muted">Payload (claims)</h5>
          <Json value={inspection.claims} label="Decoded claims" />
          <p className="mt-2 text-xs text-muted">Signature: {inspection.signaturePreview}</p>
        </div>

        <div data-testid="verify-panel" className="rounded border border-line p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-sm font-semibold">2. Verification: trusting it (needs the key)</h4>
            <ModeBadge provenance={verification.provenance} />
          </div>
          <p
            className={`mt-2 rounded p-2 text-sm font-medium ${
              verification.valid ? 'bg-success-soft text-success' : 'bg-error-soft text-error'
            }`}
            data-testid="verdict"
          >
            {verification.valid ? '✓ Accepted by the JwtDecoder' : `✗ Rejected: ${verification.category}`}
          </p>
          <p className="mt-1 break-words font-mono text-xs text-fg-2" data-testid="framework-message">
            {verification.frameworkMessage}
          </p>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <h5 className="text-xs font-semibold text-muted">The checks, in the order the decoder applies them</h5>
            <ModeBadge provenance="DERIVED" />
          </div>
          <ol className="mt-1 space-y-1 text-sm" aria-label="Verification checks">
            {inspection.stages.map((stage) => (
              <li key={stage.id} className={STATUS_STYLE[stage.status]} data-status={stage.status}>
                <span aria-hidden="true">{STATUS_ICON[stage.status]} </span>
                {stage.label}
                <span className="sr-only"> - {stage.status.toLowerCase().replace('_', ' ')}</span>
                {stage.status === 'FAILED' && <span className="block text-xs">{stage.detail}</span>}
              </li>
            ))}
          </ol>
          <p className="mt-2 text-xs text-muted">
            The verdict is the real lab decoder's own answer. The list of checks is rebuilt by running the same
            framework components one at a time (the decoder itself reports only the first failure).
          </p>
        </div>
      </div>

      <p className="rounded bg-warning-soft p-2 text-sm text-warning" role="note">
        <strong>Decoding is not verifying.</strong> You can read every claim above, including ones in a forged or expired
        token. Only the signature check shows the token is genuine and unaltered, and only the claim checks show it is
        current and meant for this application.
      </p>
    </section>
  )
}
