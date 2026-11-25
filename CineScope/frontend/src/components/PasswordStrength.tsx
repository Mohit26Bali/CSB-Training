import { passwordRules, passwordStrength, MAX_LENGTH, MIN_LENGTH, type PasswordRule, type WhoIsTyping } from '../lib/password'

/**
 * The live password helper under the password box of the sign-up form (pages/RegisterPage). It tells the person only what
 * they need to know right now, instead of a wall of rules:
 *   - before they type, one calm line says what a password needs;
 *   - while they type, a strength bar, then "Still needed" with ONLY the requirements not yet met (each one disappears the
 *     moment it is satisfied), and finally "All requirements met";
 *   - a MISTAKE - repeating a character too often, a common password, or the person's own username or email inside it - is
 *     called out in plain words in red as soon as it happens.
 * The rules come from lib/password.ts, a copy of the backend's password policy, so what is ticked off here is what the
 * server will accept. The username and email, when typed, feed the "contains your username or email" mistake.
 */
const MISTAKES: Partial<Record<PasswordRule['id'], string>> = {
  repeat: 'Avoid repeating the same character more than 3 times in a row.',
  common: 'That password is too common. Choose something harder to guess.',
  identity: 'Your password contains your username or email. Choose something different.',
}

const barColors = ['bg-line', 'bg-rose-500', 'bg-amber-500', 'bg-emerald-500', 'bg-emerald-400']

export function PasswordStrength({ password, username, email }: { password: string } & WhoIsTyping) {
  if (!password) {
    return (
      <p data-testid="password-hint" className="mt-2 text-xs text-faint">
        Use {MIN_LENGTH} to {MAX_LENGTH} characters with upper and lower case letters, a digit and a symbol.
      </p>
    )
  }

  const who = { username, email }
  const rules = passwordRules(password, who)
  const strength = passwordStrength(password, who)
  const mistakes = rules.filter((r) => !r.met && MISTAKES[r.id])
  const needed = rules.filter((r) => !r.met && !MISTAKES[r.id])

  return (
    <div className="mt-2" aria-live="polite">
      <div className="flex items-center gap-3">
        <div className="flex flex-1 gap-1" aria-hidden="true">
          {[1, 2, 3, 4].map((step) => (
            <span key={step} className={`h-1.5 flex-1 rounded-full ${step <= strength.level ? barColors[strength.level] : 'bg-line'}`} />
          ))}
        </div>
        <span className="w-14 text-right text-xs font-semibold text-muted" data-testid="password-strength">
          {strength.label}
        </span>
      </div>

      {mistakes.map((rule) => (
        <p key={rule.id} data-mistake={rule.id} className="mt-2 flex items-start gap-2 text-xs font-semibold text-rose-400">
          <span aria-hidden="true">!</span>
          {MISTAKES[rule.id]}
        </p>
      ))}

      {needed.length > 0 ? (
        <div className="mt-2 text-xs text-muted">
          <span className="font-semibold">Still needed:</span>
          <ul aria-label="Still needed" className="mt-1 flex flex-wrap gap-1.5">
            {needed.map((rule) => (
              <li key={rule.id} data-rule={rule.id} className="rounded-full border border-line px-2.5 py-0.5">
                {rule.label}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        mistakes.length === 0 && (
          <p data-testid="password-ok" className="mt-2 text-xs font-semibold text-emerald-500">
            ✓ All requirements met
          </p>
        )
      )}
    </div>
  )
}
