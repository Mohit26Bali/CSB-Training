import { expect, test } from '@playwright/test'
import { identityProblem, isCommon, passwordProblem, passwordRules, passwordStrength } from '../src/lib/password'

/**
 * The browser's copy of the password rules (src/lib/password.ts) against the same cases as the backend's
 * PasswordPolicyTest.java, so the live checklist can never promise something the server refuses (or the reverse).
 * Pure functions: no browser page is opened.
 */
test.describe('password rules (same cases as the backend tests)', () => {
  for (const password of ['Sunset-Rain-42!', 't0Mato&Basil', 'Zebra#Quartz9', 'Correct Horse 7 Bat!', 'Ünïcode-Ärger-9x', 'aA1!aA1!']) {
    test(`accepts ${password}`, () => {
      expect(passwordProblem(password)).toBeUndefined()
    })
  }

  test('names each missing kind of character', () => {
    expect(passwordProblem('lowercaseonly')).toBe('Password must include an uppercase letter, a digit and a symbol such as ! @ # or -')
    expect(passwordProblem('UPPERCASEONLY')).toBe('Password must include a lowercase letter, a digit and a symbol such as ! @ # or -')
    expect(passwordProblem('12345678901')).toBe('Password must include a lowercase letter, an uppercase letter and a symbol such as ! @ # or -')
    expect(passwordProblem('Abcdefg123')).toBe('Password must include a symbol such as ! @ # or -')
    expect(passwordProblem('Abcdefgh-ijk')).toBe('Password must include a digit')
    expect(passwordProblem('abcdefg-123')).toBe('Password must include an uppercase letter')
    expect(passwordProblem('ABCDEFG-123')).toBe('Password must include a lowercase letter')
    expect(passwordProblem('Abcdef 12345')).toBe('Password must include a symbol such as ! @ # or -') // a space is not a symbol
  })

  test('checks the length first', () => {
    expect(passwordProblem('')).toBe('Password must be at least 8 characters')
    expect(passwordProblem('Ab1!')).toBe('Password must be at least 8 characters')
    expect(passwordProblem('A'.repeat(21))).toBe('Password must be at most 20 characters')
    expect(passwordProblem('Sunset-Rain-42!abcde')).toBeUndefined() // exactly 20 characters is fine
  })

  test('rejects a character repeated more than three times in a row', () => {
    expect(passwordProblem('Gooood-Day-1')).toBe('Password must not repeat the same character more than 3 times in a row')
    expect(passwordProblem('Goood-Day-1x')).toBeUndefined()
  })

  for (const password of ['Password1!', 'P@ssw0rd!', 'Password-1', 'Qwerty123!', 'Welcome@2024', 'Admin#12345', 'Letmein-99', 'Iloveyou1!', 'Crestline-1', 'CineScope#9']) {
    test(`rejects the common password ${password}`, () => {
      expect(passwordProblem(password)).toBe('Password is too common; choose something harder to guess')
    })
  }

  test('look-alike swaps are undone only when judging common passwords', () => {
    expect(isCommon('p@$$w0rd')).toBe(true)
    expect(isCommon('Zebra#Quartz9')).toBe(false)
  })

  test('the password must not contain the username or the email name', () => {
    expect(identityProblem('Asha.Rao-2024!', { username: 'asha.rao', email: 'x@example.com' })).toBe('Password must not contain your username')
    expect(identityProblem('MyRAVI-pass1!', { username: 'Ravi' })).toBe('Password must not contain your username')
    expect(identityProblem('Meera.k#2024', { username: 'someone', email: 'meera.k@example.com' })).toBe('Password must not contain your email name')
    expect(identityProblem('Sunset-Rain-42!', { username: 'asha', email: 'asha@example.com' })).toBeNull()
    expect(identityProblem('Sunset-Rain-42!', { username: 'ab', email: 'al@example.com' })).toBeNull() // names under 3 characters are ignored
    expect(passwordProblem('My-meera.k-pass9!', { username: 'meera.k' })).toBe('Password must not contain your username')
  })

  test('the checklist reflects each rule as the password grows', () => {
    const met = (password: string, who = {}) => Object.fromEntries(passwordRules(password, who).map((r) => [r.id, r.met]))
    expect(met('')).toMatchObject({ length: false, lower: false, upper: false, digit: false, symbol: false, repeat: false, common: false })
    expect(met('abc')).toMatchObject({ length: false, lower: true, upper: false, digit: false, symbol: false, repeat: true, common: false }) // "abc" is itself a common password
    expect(met('Sunset-Rain-42!')).toEqual({ length: true, lower: true, upper: true, digit: true, symbol: true, repeat: true, common: true })
    expect(met('Password1!').common).toBe(false)
    expect(Object.keys(met('x'))).not.toContain('identity') // no username or email known yet
    expect(met('Asha-pass-1!', { username: 'asha' }).identity).toBe(false)
    expect(met('Sunset-Rain-42!', { username: 'asha' }).identity).toBe(true)
  })

  test('the strength meter', () => {
    expect(passwordStrength('')).toEqual({ level: 0, label: '' })
    expect(passwordStrength('abc').label).toBe('Weak')
    expect(passwordStrength('Sunset9!').label).toBe('Good') // all rules, 8 characters
    expect(passwordStrength('Sunset-Rain-42!').label).toBe('Strong') // all rules, 12 or more
    expect(passwordStrength('sunset-rain-42!').label).toBe('Fair') // one rule missing (no uppercase letter)
  })
})
