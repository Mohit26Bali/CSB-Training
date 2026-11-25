import { test } from 'node:test'
import assert from 'node:assert/strict'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildCatalog } from '../src/catalog.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const catalog = buildCatalog(root, 'sx_')

test('finds the 15 assignments, each with a prefixed database', () => {
  assert.equal(catalog.assignments.length, 15)
  for (const a of catalog.assignments) {
    assert.ok(a.originalDb, `${a.folder} has a CREATE DATABASE`)
    assert.equal(a.db, 'sx_' + a.originalDb)
  }
  assert.deepEqual(catalog.assignments.map((a) => a.id), ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12', '13', '14', '15'])
})

test('108 negative tests, every one with an expected MySQL error number', () => {
  const all = catalog.assignments.flatMap((a) => a.negatives)
  assert.equal(all.length, 108)
  assert.ok(all.every((n) => Number.isInteger(n.expectedCode) && n.expectedCode > 0))
})

test('classifies scripts: seed scripts build the schema, DML scripts change data, queries only read', () => {
  const bank = catalog.assignments.find((a) => a.id === '05')
  assert.deepEqual(bank.seedFiles, ['01_create_tables.sql', '02_insert_data.sql'])
  assert.deepEqual(bank.dmlScripts, ['03_inserts_updates.sql', '04_deletions.sql'])
  const org = catalog.assignments.find((a) => a.id === '01')
  assert.deepEqual(org.seedFiles, ['01_create_tables.sql', '02_insert_data.sql', '03_alter_tables.sql'])
  assert.equal(org.dmlScripts.length, 0)
  for (const a of catalog.assignments) for (const q of a.queries) assert.match(q.sql, /^\s*(SELECT|WITH|\()/i, `${a.id}/${q.id} is a SELECT`)
})

test('query ids are unique within an assignment', () => {
  for (const a of catalog.assignments) assert.equal(new Set(a.queries.map((q) => q.id)).size, a.queries.length, a.folder)
})
