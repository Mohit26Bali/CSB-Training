// Everything that touches the database. Three kinds of work, with the least rights each one needs:
//   1. schema + table browsing      -> read-only pool, identifiers checked against information_schema
//   2. the assignments' own queries and the "playground"  -> read-only pool, READ ONLY transaction, time and row limits
//   3. the data-changing scripts and negative tests  -> read-write pool, inside ONE transaction that is always rolled back
import { quoteIdent, describeError } from './db.js'
import { splitStatements, firstKeyword } from './sqlsplit.js'
import { isTransactionControl } from './catalog.js'

const QUERY_TIMEOUT_MS = 4000
const DDL_WORDS = new Set(['ALTER', 'CREATE', 'DROP', 'TRUNCATE', 'RENAME', 'GRANT', 'REVOKE'])

// ---------------------------------------------------------------- schema

/** Tables, columns, keys and foreign keys of one database, with exact row counts. */
export async function readSchema(pool, db) {
  const [tables] = await pool.query(
    `SELECT TABLE_NAME AS name FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME`,
    [db],
  )
  const [columns] = await pool.query(
    `SELECT TABLE_NAME AS tableName, COLUMN_NAME AS name, COLUMN_TYPE AS type, IS_NULLABLE = 'YES' AS nullable,
            COLUMN_KEY AS columnKey, COLUMN_DEFAULT AS defaultValue, EXTRA AS extra
       FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION`,
    [db],
  )
  const [fks] = await pool.query(
    `SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, REFERENCED_TABLE_NAME AS refTable, REFERENCED_COLUMN_NAME AS refColumn,
            CONSTRAINT_NAME AS constraintName
       FROM information_schema.KEY_COLUMN_USAGE
      WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION`,
    [db],
  )
  const [checks] = await pool.query(
    `SELECT tc.TABLE_NAME AS tableName, cc.CONSTRAINT_NAME AS name, cc.CHECK_CLAUSE AS clause
       FROM information_schema.CHECK_CONSTRAINTS cc
       JOIN information_schema.TABLE_CONSTRAINTS tc ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
      WHERE cc.CONSTRAINT_SCHEMA = ? ORDER BY tc.TABLE_NAME, cc.CONSTRAINT_NAME`,
    [db],
  )
  const result = []
  for (const t of tables) {
    const [[{ n }]] = await pool.query(`SELECT COUNT(*) AS n FROM ${quoteIdent(db)}.${quoteIdent(t.name)}`)
    result.push({
      name: t.name,
      rowCount: Number(n),
      columns: columns
        .filter((c) => c.tableName === t.name)
        .map((c) => ({ name: c.name, type: c.type, nullable: !!c.nullable, key: c.columnKey, defaultValue: c.defaultValue, extra: c.extra })),
      foreignKeys: fks.filter((f) => f.tableName === t.name).map((f) => ({ column: f.columnName, refTable: f.refTable, refColumn: f.refColumn, name: f.constraintName })),
      checks: checks.filter((c) => c.tableName === t.name).map((c) => ({ name: c.name, clause: c.clause })),
    })
  }
  return result
}

/** One page of a table. The table name must be one that exists in this database (checked, not trusted). */
export async function browseTable(pool, db, table, page, pageSize) {
  const schema = await readSchema(pool, db)
  const t = schema.find((x) => x.name === table)
  if (!t) return null
  const pk = t.columns.filter((c) => c.key === 'PRI').map((c) => quoteIdent(c.name))
  const order = pk.length ? ` ORDER BY ${pk.join(', ')}` : ''
  const size = Math.min(Math.max(Number.parseInt(pageSize, 10) || 20, 1), 100)
  const pg = Math.max(Number.parseInt(page, 10) || 1, 1)
  const [rows, fields] = await pool.query({
    sql: `SELECT * FROM ${quoteIdent(db)}.${quoteIdent(t.name)}${order} LIMIT ${size} OFFSET ${(pg - 1) * size}`,
    rowsAsArray: true,
  })
  return { table: t.name, columns: fields.map((f) => f.name), rows, page: pg, pageSize: size, totalRows: t.rowCount, totalPages: Math.max(Math.ceil(t.rowCount / size), 1) }
}

// ---------------------------------------------------------------- read-only queries

/** Accepts exactly one read-only statement. Returns an error message, or null when fine. */
export function validateReadOnly(sql) {
  if (typeof sql !== 'string' || !sql.trim()) return 'Type a query first.'
  if (sql.length > 5000) return 'The query is too long (limit 5000 characters).'
  const statements = splitStatements(sql)
  if (statements.length !== 1) return 'Run one statement at a time.'
  const text = statements[0].text
  const kw = firstKeyword(text)
  if (!['SELECT', 'WITH', '(', 'SHOW', 'DESCRIBE', 'DESC', 'EXPLAIN'].includes(kw)) return 'Only SELECT, WITH, SHOW, DESCRIBE and EXPLAIN statements are allowed here.'
  if (/\bINTO\s+(OUTFILE|DUMPFILE)\b/i.test(text)) return 'INTO OUTFILE / DUMPFILE is not allowed.'
  if (/\bFOR\s+(UPDATE|SHARE)\b|\bLOCK\s+IN\s+SHARE\s+MODE\b/i.test(text)) return 'Locking reads are not allowed.'
  return null
}

async function useDb(conn, db) {
  await conn.query(`USE ${quoteIdent(db)}`)
}

/** Statements that return rows are streamed; everything else (INSERT, UPDATE, DELETE...) returns just a summary. */
const ROW_STATEMENTS = new Set(['SELECT', 'WITH', '(', 'SHOW', 'DESCRIBE', 'DESC', 'EXPLAIN'])

/** Runs one statement on `conn`, keeping at most maxRows rows in memory (the rest are counted, not stored). */
async function streamStatement(conn, sql, maxRows) {
  if (!ROW_STATEMENTS.has(firstKeyword(sql))) {
    const [header] = await conn.query({ sql, timeout: QUERY_TIMEOUT_MS })
    return { kind: 'change', affectedRows: header.affectedRows, changedRows: header.changedRows ?? null, insertId: header.insertId || null, info: header.info || '' }
  }
  return new Promise((resolve, reject) => {
    const rows = []
    let columns = []
    let total = 0
    const q = conn.connection.query({ sql, rowsAsArray: true, timeout: QUERY_TIMEOUT_MS })
    q.on('error', reject)
    q.on('fields', (f) => { columns = f.map((x) => x.name) })
    q.on('result', (row) => {
      total++
      if (rows.length < maxRows) rows.push(row)
    })
    q.on('end', () => resolve({ kind: 'rows', columns, rows, totalRows: total, truncated: total > rows.length }))
  })
}

/** Runs read-only SQL (a catalog query or the playground) in a READ ONLY transaction with a time limit. */
export async function runReadOnly(pool, db, sql, maxRows) {
  const conn = await pool.getConnection()
  const started = Date.now()
  try {
    await useDb(conn, db)
    await conn.query('SET SESSION MAX_EXECUTION_TIME = ' + QUERY_TIMEOUT_MS)
    await conn.query('SET SESSION TRANSACTION READ ONLY')
    const result = await streamStatement(conn, sql, maxRows)
    return { ...result, ms: Date.now() - started }
  } catch (err) {
    return { kind: 'error', error: describeError(err), ms: Date.now() - started }
  } finally {
    try { await conn.query('SET SESSION TRANSACTION READ WRITE') } catch { /* connection may be gone */ }
    conn.release()
  }
}

// ---------------------------------------------------------------- sandbox (always rolled back)

const locks = new Map()
/** Sandbox runs for the same database are queued, so their row locks never collide. */
function withDbLock(db, fn) {
  const previous = locks.get(db) ?? Promise.resolve()
  const next = previous.catch(() => {}).then(fn)
  locks.set(db, next.catch(() => {}))
  return next
}

/**
 * Executes statements in one transaction on the read-write pool and ALWAYS rolls it back, so the demo data never changes.
 *  - `apply`: scripts whose statements run first, silently (earlier steps of the assignment)
 *  - `steps`: what to report. Each is run in order, and its outcome (rows, affected rows or the error) is returned.
 * The scripts contain their own START TRANSACTION / COMMIT / ROLLBACK; inside the sandbox these become savepoints
 * (START -> SAVEPOINT, COMMIT -> RELEASE SAVEPOINT, ROLLBACK -> ROLLBACK TO SAVEPOINT), so they behave as written but nothing is saved.
 */
export function runSandbox(pool, db, { apply = [], steps = [], isolateEach = false }, maxRows) {
  return withDbLock(db, async () => {
    const conn = await pool.getConnection()
    const stack = []
    let spCounter = 0
    const report = []

    async function execute(statement, capture) {
      const kw = firstKeyword(statement.text)
      if (kw === 'USE') return
      if (DDL_WORDS.has(kw)) {
        if (capture) report.push({ ...base(statement), kind: 'error', error: { code: null, message: 'Schema-changing statements are not run in the sandbox.' } })
        return
      }
      if (isTransactionControl(statement.text)) {
        let note
        let sql
        if (kw === 'START' || kw === 'BEGIN') {
          const name = `sx_sp_${++spCounter}`
          stack.push(name)
          sql = `SAVEPOINT ${name}`
          note = 'START TRANSACTION is a savepoint here'
        } else if (kw === 'COMMIT') {
          const name = stack.pop()
          sql = name ? `RELEASE SAVEPOINT ${name}` : null
          note = 'COMMIT is not saved in the sandbox'
        } else if (kw === 'ROLLBACK') {
          const name = stack.pop()
          sql = name ? `ROLLBACK TO SAVEPOINT ${name}` : null
          note = 'ROLLBACK undoes everything since START TRANSACTION'
        } else {
          sql = null
          note = 'ignored in the sandbox'
        }
        if (sql) await conn.query(sql)
        if (capture) report.push({ ...base(statement), kind: 'transaction', note })
        return
      }
      if (kw === 'SET') {
        try {
          await conn.query(statement.text)
          if (capture) report.push({ ...base(statement), kind: 'setting' })
        } catch (err) {
          if (capture) report.push({ ...base(statement), kind: 'error', error: describeError(err) })
        }
        return
      }
      try {
        const result = await streamStatement(conn, statement.text, maxRows)
        if (capture) report.push({ ...base(statement), ...result })
      } catch (err) {
        if (capture) report.push({ ...base(statement), kind: 'error', error: describeError(err) })
      }
    }
    const base = (s) => ({ sql: s.text, comments: s.comments, line: s.line, file: s.file })

    try {
      await useDb(conn, db)
      await conn.query('SET SESSION MAX_EXECUTION_TIME = ' + QUERY_TIMEOUT_MS)
      await conn.beginTransaction()
      for (const statements of apply) for (const s of statements) await execute(s, false)
      for (const s of steps) {
        if (isolateEach) {
          const name = `sx_iso_${++spCounter}`
          await conn.query(`SAVEPOINT ${name}`)
          await execute(s, true)
          await conn.query(`ROLLBACK TO SAVEPOINT ${name}`)
        } else {
          await execute(s, true)
        }
      }
      return report
    } finally {
      try { await conn.rollback() } catch { /* connection may be gone */ }
      conn.release()
    }
  })
}

/** Adds file names to parsed statements so reports can say where each one came from. */
export function tagStatements(statements, file) {
  return statements.map((s) => ({ ...s, file }))
}
