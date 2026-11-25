/**
 * The password strength rules, as the sign-up form shows them while the person types. This is a copy of the backend's
 * rules (backend security/PasswordPolicy.java), which stay the authority and re-check everything: 8 to 20 characters, a
 * lowercase letter, an uppercase letter, a digit and a symbol, the same character not more than three times in a row,
 * not a common password, and not containing the person's username or email name. e2e/password-policy.spec.ts runs the same
 * cases as the Java tests against this file, so the two cannot drift apart unnoticed. Used by components/PasswordStrength
 * (the live checklist) and by the sign-up page (to stop a weak password before it is sent).
 */
export const MIN_LENGTH = 8
export const MAX_LENGTH = 20
const MAX_REPEAT = 3
const MIN_IDENTITY_LENGTH = 3

/** Letters only, lower case. Keep in step with COMMON in the backend's PasswordPolicy.java. */
const COMMON = new Set([
  'abc', 'abcd', 'abcde', 'abcdef', 'abcdefg', 'abcdefgh', 'access', 'admin', 'administrator', 'andrew', 'asdf',
  'asdfgh', 'asdfghjkl', 'ashley', 'bank', 'banking', 'baseball', 'batman', 'bollywood', 'buster', 'changeme',
  'charlie', 'cheese', 'chocolate', 'cinescope', 'computer', 'crestline', 'cricket', 'daniel', 'default', 'dragon',
  'family', 'flower', 'football', 'freedom', 'ganesh', 'george', 'google', 'guest', 'harley', 'hello', 'helloworld',
  'hockey', 'hunter', 'iloveu', 'iloveyou', 'india', 'internet', 'jennifer', 'jessica', 'jordan', 'killer',
  'krishna', 'letmein', 'login', 'lovely', 'loveyou', 'master', 'michael', 'mobile', 'monkey', 'movie', 'movies',
  'mustang', 'mypass', 'mypassword', 'naruto', 'newpassword', 'ninja', 'pass', 'passcode', 'passwd', 'password',
  'pepper', 'pokemon', 'princess', 'qazwsx', 'qwerty', 'qwertyu', 'qwertyui', 'qwertyuiop', 'rahul', 'ranger',
  'robert', 'root', 'sachin', 'samsung', 'secret', 'shadow', 'shiva', 'soccer', 'spring', 'starwars', 'summer',
  'sunshine', 'superman', 'test', 'testing', 'thomas', 'toor', 'trustno', 'user', 'welcome', 'whatever', 'winter',
  'yourpassword', 'zxcvbn', 'zxcvbnm',
])

export interface PasswordRule {
  id: 'length' | 'lower' | 'upper' | 'digit' | 'symbol' | 'repeat' | 'common' | 'identity'
  /** What the checklist shows, phrased as the thing to achieve. */
  label: string
  met: boolean
}

export interface WhoIsTyping {
  username?: string
  email?: string
}

const SWAPS: Record<string, string> = { '@': 'a', '4': 'a', '3': 'e', '0': 'o', $: 's', '5': 's', '7': 't' }

function lettersOf(lower: string, undoSwaps: boolean): string {
  let out = ''
  for (const ch of lower) {
    const use = undoSwaps ? (SWAPS[ch] ?? ch) : ch
    if (use >= 'a' && use <= 'z') out += use
  }
  return out
}

export function isCommon(password: string): boolean {
  const lower = password.toLowerCase()
  return COMMON.has(lower) || COMMON.has(lettersOf(lower, false)) || COMMON.has(lettersOf(lower, true))
}

function hasLongRepeat(password: string): boolean {
  let run = 1
  const chars = [...password]
  for (let i = 1; i < chars.length; i++) {
    run = chars[i] === chars[i - 1] ? run + 1 : 1
    if (run > MAX_REPEAT) return true
  }
  return false
}

/** Why the password contains the person's own details, or null. */
export function identityProblem(password: string, who: WhoIsTyping = {}): string | null {
  const lower = password.toLowerCase()
  const username = (who.username ?? '').trim().toLowerCase()
  if (username.length >= MIN_IDENTITY_LENGTH && lower.includes(username)) return 'Password must not contain your username'
  const email = (who.email ?? '').trim().toLowerCase()
  const at = email.indexOf('@')
  const name = at > 0 ? email.slice(0, at) : email
  if (name.length >= MIN_IDENTITY_LENGTH && lower.includes(name)) return 'Password must not contain your email name'
  return null
}

/** Every rule with whether the password meets it right now (the live checklist). The identity rule appears only once a username or email is known. */
export function passwordRules(password: string, who: WhoIsTyping = {}): PasswordRule[] {
  const rules: PasswordRule[] = [
    { id: 'length', label: `${MIN_LENGTH} to ${MAX_LENGTH} characters`, met: password.length >= MIN_LENGTH && password.length <= MAX_LENGTH },
    { id: 'lower', label: 'A lowercase letter', met: /\p{Ll}/u.test(password) },
    { id: 'upper', label: 'An uppercase letter', met: /\p{Lu}/u.test(password) },
    { id: 'digit', label: 'A digit', met: /\p{Nd}/u.test(password) },
    { id: 'symbol', label: 'A symbol such as ! @ # or -', met: /[^\p{L}\p{N}\s]/u.test(password) },
    { id: 'repeat', label: 'No character repeated more than 3 times in a row', met: password.length > 0 && !hasLongRepeat(password) },
    { id: 'common', label: 'Not a common password', met: password.length > 0 && !isCommon(password) },
  ]
  if ((who.username ?? '').trim().length >= MIN_IDENTITY_LENGTH || (who.email ?? '').includes('@')) {
    rules.push({ id: 'identity', label: 'Does not contain your username or email', met: password.length > 0 && identityProblem(password, who) === null })
  }
  return rules
}

function join(parts: string[]): string {
  return parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`
}

/** The message to show when the password is not acceptable (same wording as the backend), or undefined when it is fine. */
export function passwordProblem(password: string, who: WhoIsTyping = {}): string | undefined {
  if (password.length < MIN_LENGTH) return `Password must be at least ${MIN_LENGTH} characters`
  if (password.length > MAX_LENGTH) return `Password must be at most ${MAX_LENGTH} characters`
  const missing: string[] = []
  if (!/\p{Ll}/u.test(password)) missing.push('a lowercase letter')
  if (!/\p{Lu}/u.test(password)) missing.push('an uppercase letter')
  if (!/\p{Nd}/u.test(password)) missing.push('a digit')
  if (!/[^\p{L}\p{N}\s]/u.test(password)) missing.push('a symbol such as ! @ # or -')
  if (missing.length > 0) return `Password must include ${join(missing)}`
  if (hasLongRepeat(password)) return `Password must not repeat the same character more than ${MAX_REPEAT} times in a row`
  if (isCommon(password)) return 'Password is too common; choose something harder to guess'
  return identityProblem(password, who) ?? undefined
}

export type Strength = { level: 0 | 1 | 2 | 3 | 4; label: string }

/** A rough meter for the bar under the field: nothing typed, Weak, Fair, Good (all rules met) or Strong (all met and 12+ characters). */
export function passwordStrength(password: string, who: WhoIsTyping = {}): Strength {
  if (!password) return { level: 0, label: '' }
  const unmet = passwordRules(password, who).filter((r) => !r.met).length
  if (unmet === 0) return password.length >= 12 ? { level: 4, label: 'Strong' } : { level: 3, label: 'Good' }
  return unmet === 1 ? { level: 2, label: 'Fair' } : { level: 1, label: 'Weak' }
}
