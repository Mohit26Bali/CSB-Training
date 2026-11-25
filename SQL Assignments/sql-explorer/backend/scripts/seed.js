// Builds the demo databases from the assignments' own scripts (create tables, insert data, alter tables) and creates the
// two limited users the app connects with. Safe to run again: it rebuilds the prefixed databases from scratch.
//
//   npm run seed -- --yes
//
// SAFETY: every database it creates or drops starts with DB_PREFIX (default "sx_"), e.g. sx_org_db, so it can never touch a
// database you made yourself by running the assignments (org_db, bank_db, ...). The admin login is used only here.
import mysql from 'mysql2/promise'
import { loadConfig } from '../src/config.js'
import { buildCatalog } from '../src/catalog.js'
import { quoteIdent } from '../src/db.js'

const config = loadConfig()
const adminUser = process.env.ADMIN_USER ?? 'root'
if (process.env.ADMIN_PASSWORD === undefined) {
  console.error('ADMIN_PASSWORD is not set (it is used only by this seed script; an empty value is fine for a passwordless local server).')
  process.exit(1)
}
if (!process.argv.includes('--yes')) {
  console.error(`This will DROP and recreate the ${config.prefix}* databases on ${config.db.host}:${config.db.port} as "${adminUser}".\nRun again with --yes to continue:  npm run seed -- --yes`)
  process.exit(1)
}

const catalog = buildCatalog(config.assignmentsDir, config.prefix)
const conn = await mysql.createConnection({
  host: config.db.host,
  port: config.db.port,
  user: adminUser,
  password: process.env.ADMIN_PASSWORD,
  multipleStatements: true,
})

/** Point the script's `CREATE DATABASE x` / `USE x` at the prefixed name. Nothing else in the scripts names the database. */
function rewriteDb(sql) {
  return sql.replace(/^(\s*(?:CREATE\s+DATABASE|USE)\s+)`?(\w+)`?/gim, (_, head, name) => `${head}${quoteIdent(config.prefix + name)}`)
}

try {
  console.log(`Seeding ${catalog.assignments.length} assignments on ${config.db.host}:${config.db.port} (databases ${config.prefix}*)`)
  for (const a of catalog.assignments) {
    if (!a.db.startsWith(config.prefix)) throw new Error(`Refusing to touch ${a.db}: it does not start with ${config.prefix}`)
    await conn.query(`DROP DATABASE IF EXISTS ${quoteIdent(a.db)}`)
    for (const file of a.seedFiles) {
      const script = a.scripts.find((s) => s.file === file)
      try {
        await conn.query(rewriteDb(script.source))
      } catch (err) {
        throw new Error(`${a.folder}/${file}: ${err.sqlMessage ?? err.message}`)
      }
    }
    const [tables] = await conn.query(`SELECT COUNT(*) AS n FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?`, [a.db])
    console.log(`  ${a.id}  ${a.db.padEnd(28)} ${tables[0].n} tables  (${a.seedFiles.join(', ')})`)
  }

  // the two app users: read-only, and read-write without any schema rights (used for the rolled-back sandbox only)
  const hosts = ['localhost', '127.0.0.1', '::1']
  const users = [
    { name: config.ro.user, password: config.ro.password, privileges: 'SELECT' },
    { name: config.rw.user, password: config.rw.password, privileges: 'SELECT, INSERT, UPDATE, DELETE' },
  ]
  for (const u of users) {
    for (const host of hosts) {
      await conn.query(`CREATE USER IF NOT EXISTS ?@? IDENTIFIED BY ?`, [u.name, host, u.password])
      await conn.query(`ALTER USER ?@? IDENTIFIED BY ?`, [u.name, host, u.password])
      for (const a of catalog.assignments) {
        await conn.query(`GRANT ${u.privileges} ON ${quoteIdent(a.db)}.* TO ?@?`, [u.name, host])
      }
    }
    console.log(`  user ${u.name}: ${u.privileges} on ${config.prefix}* only`)
  }
  console.log('Done.')
} finally {
  await conn.end()
}
