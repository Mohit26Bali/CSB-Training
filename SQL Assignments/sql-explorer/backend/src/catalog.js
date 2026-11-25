// Builds the catalog of assignments by reading the assignment folders themselves (the .sql files and README.md),
// so the app can never drift away from what the assignments contain. Nothing here talks to the database.
import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { splitStatements, firstKeyword } from './sqlsplit.js'

const DDL_WORDS = new Set(['ALTER', 'CREATE', 'DROP', 'TRUNCATE', 'RENAME', 'GRANT', 'REVOKE'])
const WRITE_WORDS = new Set(['INSERT', 'UPDATE', 'DELETE', 'REPLACE'])
const TX_WORDS = new Set(['START', 'BEGIN', 'COMMIT', 'ROLLBACK', 'SAVEPOINT', 'RELEASE'])

/** What a script is for. 'seed' kinds (schema, seed, alter) build the demo database; the others are demonstrations. */
function classify(file, statements) {
  if (/create_tables/i.test(file)) return 'schema'
  if (/insert_data/i.test(file)) return 'seed'
  if (/alter_tables/i.test(file)) return 'alter'
  if (/negative/i.test(file)) return 'negative'
  const words = statements.map((s) => firstKeyword(s.text)).filter((w) => w !== 'USE' && w !== 'SET')
  if (words.every((w) => w === 'SELECT' || w === 'WITH' || w === '(')) return 'queries'
  if (words.some((w) => DDL_WORDS.has(w))) return 'other'
  if (words.some((w) => WRITE_WORDS.has(w))) return 'dml'
  return 'other'
}

function pickLabel(comments, fallback) {
  for (let k = comments.length - 1; k >= 0; k--) {
    const m = /^([a-z]+\d+)\s*[:\-]\s*(.+)$/i.exec(comments[k])
    if (m) return { label: m[1].toLowerCase(), title: m[2].trim() }
  }
  const last = comments.filter(Boolean).slice(-1)[0]
  return { label: fallback, title: last || fallback }
}

function readmeTitle(readme, fallback) {
  const m = /^#\s+(.+)$/m.exec(readme)
  return (m ? m[1] : fallback).trim()
}

/**
 * @param {string} root folder that contains the 01-..., 02-... assignment folders
 * @param {string} prefix prepended to every database name, so the demo never touches an existing database of the same name
 */
export function buildCatalog(root, prefix = 'sx_') {
  const folders = readdirSync(root)
    .filter((d) => /^\d\d-/.test(d) && statSync(join(root, d)).isDirectory())
    .sort()

  const assignments = folders.map((folder) => {
    const dir = join(root, folder)
    const id = folder.slice(0, 2)
    const readmePath = join(dir, 'README.md')
    const readme = existsSync(readmePath) ? readFileSync(readmePath, 'utf8') : ''
    const sqlFiles = readdirSync(dir).filter((f) => f.endsWith('.sql')).sort()

    let originalDb = null
    const scripts = sqlFiles.map((file) => {
      const source = readFileSync(join(dir, file), 'utf8')
      const statements = splitStatements(source)
      const created = statements.map((s) => /^CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?/i.exec(s.text)).find(Boolean)
      if (created && !originalDb) originalDb = created[1]
      return { file, kind: classify(file, statements), source, statements }
    })

    const category = /-DDL$/.test(folder) ? 'DDL' : /-DML$/.test(folder) ? 'DML' : /-SELECT$/.test(folder) ? 'SELECT' : 'Capstone'

    // queries: every SELECT in a 'queries' script, with its q1 / r1 label and description taken from the comment above it
    const queries = []
    for (const script of scripts.filter((s) => s.kind === 'queries')) {
      script.statements.forEach((st, index) => {
        const kw = firstKeyword(st.text)
        if (kw === 'USE' || kw === 'SET') return
        const { label, title } = pickLabel(st.comments, `s${index + 1}`)
        let queryId = label
        if (queries.some((q) => q.id === queryId)) queryId = `${script.file.slice(0, 2)}-${label}`
        queries.push({ id: queryId, title, sql: st.text, file: script.file })
      })
    }

    // negative tests: statements that are supposed to fail; the expected MySQL error number is in the comment above
    const negatives = []
    for (const script of scripts.filter((s) => s.kind === 'negative')) {
      for (const st of script.statements) {
        const kw = firstKeyword(st.text)
        if (kw === 'USE' || kw === 'SET') continue
        const note = st.comments.find((c) => /\(error \d+\)/i.test(c))
        if (!note) continue // the row-count checks at the end of the file
        const m = /^([a-z]+\d+)\s+(.*?)\s*\(error (\d+)\)/i.exec(note)
        negatives.push({
          id: (m ? m[1] : `t${negatives.length + 1}`).toLowerCase(),
          title: m ? m[2].trim() : note,
          expectedCode: Number(/\(error (\d+)\)/i.exec(note)[1]),
          sql: st.text,
        })
      }
    }

    // data-changing scripts, in file order. They are demonstrated inside a transaction that is always rolled back.
    const dmlScripts = scripts.filter((s) => s.kind === 'dml').map((s) => s.file)

    return {
      id,
      folder,
      slug: folder,
      title: readmeTitle(readme, folder),
      category,
      originalDb,
      db: originalDb ? prefix + originalDb : null,
      readme,
      scripts: scripts.map(({ file, kind, source, statements }) => ({ file, kind, source, statements: statements.length })),
      seedFiles: scripts.filter((s) => ['schema', 'seed', 'alter'].includes(s.kind)).map((s) => s.file),
      dmlScripts,
      queries,
      negatives,
      // internal (not sent to the browser): the parsed statements per script
      _statements: Object.fromEntries(scripts.map((s) => [s.file, s.statements])),
    }
  })

  return { prefix, root, assignments }
}

/** The assignment as sent to the browser (no parsed internals). */
export function publicAssignment(a) {
  const { _statements, ...rest } = a
  return rest
}

export function isTransactionControl(sql) {
  return TX_WORDS.has(firstKeyword(sql))
}
