import { test } from 'node:test'
import assert from 'node:assert/strict'
import { splitStatements, firstKeyword } from '../src/sqlsplit.js'

test('splits on semicolons and keeps the comments above each statement', () => {
  const s = splitStatements('-- section\n-- q1: first\nSELECT 1;\n\n-- q2: second\nSELECT 2;')
  assert.equal(s.length, 2)
  assert.deepEqual(s[0].comments, ['section', 'q1: first'])
  assert.deepEqual(s[1].comments, ['q2: second'])
  assert.equal(s[1].text, 'SELECT 2')
})

test('a semicolon inside a string or a comment does not end a statement', () => {
  const s = splitStatements("INSERT INTO t VALUES ('a;b', \"c;d\", `e;f`); -- trailing; comment\nSELECT 'it''s; fine';")
  assert.equal(s.length, 2)
  assert.match(s[0].text, /'a;b'/)
  assert.match(s[1].text, /it''s; fine/)
})

test('block comments and # comments are skipped', () => {
  const s = splitStatements('/* header; with semicolon */ SELECT 1; # note\nSELECT 2;')
  assert.deepEqual(s.map((x) => x.text), ['SELECT 1', 'SELECT 2'])
})

test('a final statement without a semicolon is kept; empty input gives nothing', () => {
  assert.equal(splitStatements('SELECT 1').length, 1)
  assert.equal(splitStatements('  \n -- only a comment\n').length, 0)
})

test('firstKeyword', () => {
  assert.equal(firstKeyword('  select 1'), 'SELECT')
  assert.equal(firstKeyword('(SELECT 1) UNION (SELECT 2)'), '(')
  assert.equal(firstKeyword('Start transaction'), 'START')
})
