import express from 'express'
import { existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { publicAssignment } from './catalog.js'
import { readSchema, browseTable, runReadOnly, runSandbox, validateReadOnly, tagStatements } from './service.js'

const here = dirname(fileURLToPath(import.meta.url))

class HttpError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

/** A tiny fixed-window rate limiter (per client address), enough to stop a runaway script. */
function rateLimit({ windowMs, max }) {
  const hits = new Map()
  return (req, res, next) => {
    const now = Date.now()
    const key = req.ip
    const entry = hits.get(key)
    if (!entry || now - entry.start > windowMs) {
      hits.set(key, { start: now, count: 1 })
      return next()
    }
    entry.count++
    if (entry.count > max) {
      res.set('Retry-After', String(Math.ceil((entry.start + windowMs - now) / 1000)))
      return res.status(429).json({ error: 'Too many requests, slow down for a moment.' })
    }
    next()
  }
}

export function createApp({ config, pools, catalog }) {
  const app = express()
  app.disable('x-powered-by')
  app.use(express.json({ limit: '20kb' }))
  app.use((req, res, next) => {
    res.set({
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'no-referrer',
      'Cross-Origin-Resource-Policy': 'same-origin',
      'Content-Security-Policy': "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'",
    })
    next()
  })
  app.use('/api', rateLimit({ windowMs: 60_000, max: config.rateLimitPerMinute }))

  const find = (id) => {
    const a = catalog.assignments.find((x) => x.id === id)
    if (!a) throw new HttpError(404, `No assignment "${id}".`)
    return a
  }

  // short cache for the home page counts (15 databases x a few tables)
  let summaryCache = { at: 0, value: null }

  app.get('/api/health', async (req, res) => {
    await pools.ro.query('SELECT 1')
    res.json({ status: 'ok', assignments: catalog.assignments.length })
  })

  app.get('/api/assignments', async (req, res) => {
    if (!summaryCache.value || Date.now() - summaryCache.at > 15_000) {
      const list = []
      for (const a of catalog.assignments) {
        const tables = await readSchema(pools.ro, a.db)
        list.push({
          id: a.id,
          title: a.title,
          category: a.category,
          db: a.db,
          originalDb: a.originalDb,
          tables: tables.length,
          rows: tables.reduce((sum, t) => sum + t.rowCount, 0),
          queries: a.queries.length,
          negatives: a.negatives.length,
          dml: a.dmlScripts.length,
        })
      }
      summaryCache = { at: Date.now(), value: list }
    }
    res.json(summaryCache.value)
  })

  app.get('/api/assignments/:id', async (req, res) => {
    const a = find(req.params.id)
    const tables = await readSchema(pools.ro, a.db)
    res.json({ ...publicAssignment(a), tables })
  })

  app.get('/api/assignments/:id/tables/:table', async (req, res) => {
    const a = find(req.params.id)
    const data = await browseTable(pools.ro, a.db, req.params.table, req.query.page, req.query.pageSize)
    if (!data) throw new HttpError(404, `No table "${req.params.table}" in ${a.originalDb}.`)
    res.json(data)
  })

  // one of the assignment's own queries. mode "seed" = on the seed data; "after-scripts" = after its data-changing scripts (rolled back)
  app.post('/api/assignments/:id/queries/:qid/run', async (req, res) => {
    const a = find(req.params.id)
    const q = a.queries.find((x) => x.id === req.params.qid)
    if (!q) throw new HttpError(404, `No query "${req.params.qid}".`)
    const mode = req.body?.mode === 'after-scripts' ? 'after-scripts' : 'seed'
    if (mode === 'after-scripts' && a.dmlScripts.length === 0) throw new HttpError(400, 'This assignment has no data-changing scripts.')
    if (mode === 'seed') {
      return res.json({ mode, ...(await runReadOnly(pools.ro, a.db, q.sql, config.maxRows)) })
    }
    const apply = a.dmlScripts.map((f) => tagStatements(a._statements[f], f))
    const [step] = await runSandbox(pools.rw, a.db, { apply, steps: [{ text: q.sql, comments: [], line: 0, file: q.file }] }, config.maxRows)
    res.json({ mode, ...step })
  })

  // a data-changing script, run for real inside a transaction that is rolled back afterwards
  app.post('/api/assignments/:id/scripts/:file/run', async (req, res) => {
    const a = find(req.params.id)
    const idx = a.dmlScripts.indexOf(req.params.file)
    if (idx === -1) throw new HttpError(404, `"${req.params.file}" is not a data-changing script of this assignment.`)
    const apply = a.dmlScripts.slice(0, idx).map((f) => tagStatements(a._statements[f], f))
    const steps = tagStatements(a._statements[req.params.file], req.params.file)
    const report = await runSandbox(pools.rw, a.db, { apply, steps }, config.maxRows)
    res.json({ file: req.params.file, appliedBefore: a.dmlScripts.slice(0, idx), steps: report, rolledBack: true })
  })

  // the negative tests: every statement should fail with the error number the comment predicts
  app.post('/api/assignments/:id/negatives/run', async (req, res) => {
    const a = find(req.params.id)
    const apply = a.dmlScripts.map((f) => tagStatements(a._statements[f], f))
    const steps = a.negatives.map((n) => ({ text: n.sql, comments: [], line: 0, file: 'negative' }))
    const report = await runSandbox(pools.rw, a.db, { apply, steps, isolateEach: true }, config.maxRows)
    const results = a.negatives.map((n, i) => {
      const r = report[i]
      const actual = r?.kind === 'error' ? r.error : null
      return {
        id: n.id,
        title: n.title,
        sql: n.sql,
        expectedCode: n.expectedCode,
        actualCode: actual?.code ?? null,
        message: actual?.message ?? null,
        unexpectedSuccess: r?.kind !== 'error',
        ok: !!actual && actual.code === n.expectedCode,
      }
    })
    res.json({ results, passed: results.filter((r) => r.ok).length, total: results.length })
  })

  // the playground: one read-only statement of your own
  app.post('/api/assignments/:id/sql', async (req, res) => {
    const a = find(req.params.id)
    const problem = validateReadOnly(req.body?.sql)
    if (problem) throw new HttpError(400, problem)
    res.json(await runReadOnly(pools.ro, a.db, req.body.sql, config.maxRows))
  })

  app.use('/api', (req, res) => {
    res.status(404).json({ error: 'Not found.' })
  })

  // the built front end, when it exists (npm run build in ../frontend)
  const dist = resolve(here, '..', '..', 'frontend', 'dist')
  if (existsSync(join(dist, 'index.html'))) {
    app.use(express.static(dist))
    app.get(/^\/(?!api\/).*/, (req, res) => res.sendFile(join(dist, 'index.html')))
  }

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    if (err instanceof HttpError) return res.status(err.status).json({ error: err.message })
    if (err?.type === 'entity.parse.failed' || err?.type === 'entity.too.large') return res.status(400).json({ error: 'The request body is not valid.' })
    console.error('Unhandled error:', err?.message ?? err)
    res.status(500).json({ error: 'Something went wrong on the server.' })
  })

  return app
}
