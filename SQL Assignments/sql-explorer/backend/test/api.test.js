// Integration tests: they need the demo databases (npm run seed -- --yes) and a .env, and are skipped without them.
import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { loadConfig } from '../src/config.js'
import { createPools } from '../src/db.js'
import { buildCatalog } from '../src/catalog.js'
import { createApp } from '../src/app.js'

let config
try {
  config = loadConfig()
} catch {
  config = null
}
const skip = config ? false : 'no .env (copy .env.example and run npm run seed -- --yes)'

let server, base, pools, catalog

before(async () => {
  if (!config) return
  catalog = buildCatalog(config.assignmentsDir, config.prefix)
  pools = createPools(config)
  const app = createApp({ config, pools, catalog })
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve) })
  base = `http://127.0.0.1:${server.address().port}`
})

after(async () => {
  if (!config) return
  await new Promise((resolve) => server.close(resolve))
  await Promise.all([pools.ro.end(), pools.rw.end()])
})

const get = (path) => fetch(base + path).then(async (r) => ({ status: r.status, body: await r.json() }))
const post = (path, body) => fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) }).then(async (r) => ({ status: r.status, body: await r.json() }))

/** A fingerprint of every table of every demo database: any change to the data changes it. */
async function fingerprint() {
  const out = {}
  for (const a of catalog.assignments) {
    const [tables] = await pools.ro.query(`SELECT TABLE_NAME AS t FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY 1`, [a.db])
    for (const { t } of tables) {
      const [[row]] = await pools.ro.query(`CHECKSUM TABLE \`${a.db}\`.\`${t}\``)
      out[`${a.db}.${t}`] = row.Checksum
    }
  }
  return out
}

test('health and the list of assignments', { skip }, async () => {
  assert.deepEqual((await get('/api/health')).body, { status: 'ok', assignments: 15 })
  const { body } = await get('/api/assignments')
  assert.equal(body.length, 15)
  assert.ok(body.every((a) => a.tables >= 2 && a.rows > 0 && a.queries > 0), 'every database is seeded')
  const org = body.find((a) => a.id === '01')
  assert.equal(org.tables, 4)
  assert.equal(org.rows, 33)
})

test('schema of an assignment: columns, keys and foreign keys', { skip }, async () => {
  const { body } = await get('/api/assignments/01')
  const employees = body.tables.find((t) => t.name === 'employees')
  assert.ok(employees.columns.find((c) => c.name === 'emp_id').key === 'PRI')
  assert.ok(employees.columns.some((c) => c.name === 'salary' && c.nullable === false), 'the ALTER script was applied')
  assert.deepEqual(employees.foreignKeys.map((f) => [f.column, f.refTable]), [['dept_id', 'departments']])
  assert.ok(employees.checks !== undefined)
  assert.equal(body.tables.find((t) => t.name === 'projects').checks.length, 1, 'the budget CHECK constraint is reported')
})

test('table browsing pages through the rows', { skip }, async () => {
  const first = (await get('/api/assignments/01/tables/employees?pageSize=4&page=1')).body
  assert.equal(first.rows.length, 4)
  assert.equal(first.totalRows, 10)
  assert.equal(first.totalPages, 3)
  const last = (await get('/api/assignments/01/tables/employees?pageSize=4&page=3')).body
  assert.equal(last.rows.length, 2)
})

test('only real tables can be browsed: unknown names and injection attempts are 404', { skip }, async () => {
  for (const name of ['nope', 'employees;DROP%20TABLE%20employees', '%60employees%60', 'employees%20--', '..%2F..%2Fetc']) {
    assert.equal((await get(`/api/assignments/01/tables/${name}`)).status, 404, name)
  }
  assert.equal((await get('/api/assignments/99')).status, 404)
  assert.equal((await get('/api/assignments/01/tables/employees')).status, 200, 'the table is still there')
})

test('every query of every assignment runs on the seed data without an error', { skip }, async () => {
  let ran = 0
  for (const a of catalog.assignments) {
    for (const q of a.queries) {
      const { body } = await post(`/api/assignments/${a.id}/queries/${q.id}/run`)
      assert.equal(body.kind, 'rows', `${a.id}/${q.id}: ${JSON.stringify(body.error)}`)
      assert.ok(Array.isArray(body.columns) && body.columns.length > 0, `${a.id}/${q.id} has columns`)
      ran++
    }
  }
  assert.ok(ran >= 150, `ran ${ran} queries`)
})

test('every negative test of every assignment fails with the error number its comment predicts (108 of 108)', { skip }, async () => {
  let passed = 0
  for (const a of catalog.assignments) {
    const { body } = await post(`/api/assignments/${a.id}/negatives/run`)
    for (const r of body.results) {
      assert.equal(r.ok, true, `${a.id}/${r.id} expected ${r.expectedCode}, got ${r.actualCode} (${r.message})`)
      passed++
    }
  }
  assert.equal(passed, 108)
})

test('the data-changing scripts really run, show their effect, and leave the data untouched', { skip }, async () => {
  const before = await fingerprint()

  // 04: a salary raise. The script reports rows changed for each UPDATE.
  const run = (await post('/api/assignments/04/scripts/03_update_salaries.sql/run')).body
  assert.equal(run.rolledBack, true)
  const updates = run.steps.filter((s) => s.kind === 'change')
  assert.ok(updates.length >= 4 && updates.some((s) => s.affectedRows > 0), 'the UPDATE statements changed rows')
  assert.ok(run.steps.every((s) => s.kind !== 'error'), JSON.stringify(run.steps.find((s) => s.kind === 'error')))

  // 04 after-scripts: the verification query sees the changes the scripts made
  const seedSum = (await post('/api/assignments/04/queries/q1/run', { mode: 'seed' })).body
  const afterSum = (await post('/api/assignments/04/queries/q1/run', { mode: 'after-scripts' })).body
  assert.equal(afterSum.kind, 'rows')
  assert.notDeepEqual(afterSum.rows, seedSum.rows, 'the same query gives different rows after the scripts have run')

  // 05 contains START TRANSACTION / COMMIT / ROLLBACK of its own; they must work as savepoints
  const bank = (await post('/api/assignments/05/scripts/03_inserts_updates.sql/run')).body
  assert.ok(bank.steps.some((s) => s.kind === 'transaction'), 'transaction control is reported')
  assert.ok(bank.steps.every((s) => s.kind !== 'error'), JSON.stringify(bank.steps.find((s) => s.kind === 'error')))

  // every script of every assignment, then check nothing was saved
  for (const a of catalog.assignments) {
    for (const f of a.dmlScripts) {
      const { body } = await post(`/api/assignments/${a.id}/scripts/${f}/run`)
      assert.equal(body.rolledBack, true, `${a.id}/${f}`)
      assert.ok(body.steps.every((s) => s.kind !== 'error'), `${a.id}/${f}: ${JSON.stringify(body.steps.find((s) => s.kind === 'error'))}`)
    }
  }
  assert.deepEqual(await fingerprint(), before, 'the sandbox never changes the demo data')
})

test('after all of that the demo data is exactly as seeded', { skip }, async () => {
  const { body } = await get('/api/assignments')
  assert.equal(body.find((a) => a.id === '01').rows, 33)
})

test('scripts that are not data-changing scripts cannot be run', { skip }, async () => {
  assert.equal((await post('/api/assignments/01/scripts/03_alter_tables.sql/run')).status, 404)
  assert.equal((await post('/api/assignments/04/scripts/..%2F..%2Fx.sql/run')).status, 404)
  assert.equal((await post('/api/assignments/01/queries/q1/run', { mode: 'after-scripts' })).status, 400)
})

test('playground: a SELECT works; anything else is refused before it reaches MySQL', { skip }, async () => {
  const ok = (await post('/api/assignments/01/sql', { sql: 'SELECT COUNT(*) AS n FROM employees' })).body
  assert.equal(ok.rows.length, 1)
  assert.equal(Number(ok.rows[0][0]), 10)
  for (const sql of [
    'DELETE FROM employees',
    'UPDATE employees SET salary = 0',
    'DROP TABLE employees',
    'SELECT 1; DROP TABLE employees',
    "SELECT * FROM employees INTO OUTFILE '/tmp/x'",
    'SELECT * FROM employees FOR UPDATE',
    'INSERT INTO departments (dept_name, location) VALUES ("x", "y")',
    '',
    '   ',
  ]) {
    const r = await post('/api/assignments/01/sql', { sql })
    assert.equal(r.status, 400, sql)
  }
  assert.equal((await post('/api/assignments/01/sql', { sql: 5 })).status, 400)
})

test('playground: even a trick that passes the check cannot write, because the connection has no write rights', { skip }, async () => {
  const before = await fingerprint()
  for (const sql of [
    'WITH x AS (SELECT 1) SELECT * FROM x',
    'SELECT SLEEP(0)',
    'SHOW TABLES',
    'DESCRIBE employees',
    'EXPLAIN SELECT * FROM employees',
  ]) {
    const r = await post('/api/assignments/01/sql', { sql })
    assert.equal(r.status, 200, sql)
    assert.equal(r.body.kind, 'rows', `${sql} -> ${JSON.stringify(r.body.error)}`)
  }
  assert.deepEqual(await fingerprint(), before)
})

test('playground: SQL errors are reported, runaway queries are stopped, and big results are capped', { skip }, async () => {
  const bad = (await post('/api/assignments/01/sql', { sql: 'SELECT * FROM no_such_table' })).body
  assert.equal(bad.kind, 'error')
  assert.equal(bad.error.code, 1146)

  const slow = (await post('/api/assignments/01/sql', { sql: 'SELECT SLEEP(30)' })).body
  assert.ok(slow.kind === 'error' || (slow.kind === 'rows' && slow.rows[0][0] !== 0), 'SLEEP(30) was cut short')
  assert.ok(slow.ms < 8000, `stopped after ${slow.ms} ms`)

  const big = (await post('/api/assignments/01/sql', { sql: 'SELECT a.emp_id FROM employees a, employees b, employees c, employees d' })).body
  assert.equal(big.kind, 'rows')
  assert.equal(big.rows.length, config.maxRows)
  assert.equal(big.truncated, true)
  assert.equal(big.totalRows, 10000)
})

test('the API refuses malformed input without leaking internals', { skip }, async () => {
  const r = await fetch(base + '/api/assignments/01/sql', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{not json' })
  assert.equal(r.status, 400)
  assert.deepEqual(Object.keys(await r.json()), ['error'])
  const big = await fetch(base + '/api/assignments/01/sql', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sql: 'x'.repeat(30000) }) })
  assert.equal(big.status, 400)
  const nf = await get('/api/nothing-here')
  assert.equal(nf.status, 404)
  const headers = (await fetch(base + '/api/health')).headers
  assert.equal(headers.get('x-content-type-options'), 'nosniff')
  assert.equal(headers.get('x-powered-by'), null)
})

test('the rate limit answers 429 with a Retry-After once a client sends too many requests', { skip }, async () => {
  const limited = createApp({ config: { ...config, rateLimitPerMinute: 3 }, pools, catalog })
  const s = await new Promise((resolve) => { const srv = limited.listen(0, '127.0.0.1', () => resolve(srv)) })
  try {
    const url = `http://127.0.0.1:${s.address().port}/api/health`
    const statuses = []
    for (let i = 0; i < 5; i++) statuses.push((await fetch(url)).status)
    assert.deepEqual(statuses, [200, 200, 200, 429, 429])
    const blocked = await fetch(url)
    assert.ok(Number(blocked.headers.get('retry-after')) > 0)
  } finally {
    await new Promise((resolve) => s.close(resolve))
  }
})
