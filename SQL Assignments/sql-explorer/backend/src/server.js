import { loadConfig } from './config.js'
import { createPools } from './db.js'
import { buildCatalog } from './catalog.js'
import { createApp } from './app.js'

const config = loadConfig()
const catalog = buildCatalog(config.assignmentsDir, config.prefix)
const pools = createPools(config)
const app = createApp({ config, pools, catalog })

const server = app.listen(config.port, config.host, () => {
  console.log(`SQL Explorer API on http://${config.host}:${config.port}  (${catalog.assignments.length} assignments, databases ${config.prefix}*)`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => Promise.all([pools.ro.end(), pools.rw.end()]).finally(() => process.exit(0)))
  })
}
