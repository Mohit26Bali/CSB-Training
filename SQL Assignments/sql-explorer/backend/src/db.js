import mysql from 'mysql2/promise'

const common = {
  waitForConnections: true,
  connectionLimit: 5,
  dateStrings: true, // dates as plain strings: no time-zone surprises
  supportBigNumbers: true,
  bigNumberStrings: true, // exact large numbers; DECIMAL values are strings by default
  multipleStatements: false, // one statement per call, always
  charset: 'utf8mb4',
}

/** Two pools with different rights: `ro` can only SELECT, `rw` can also INSERT/UPDATE/DELETE (used only for rolled-back sandbox runs). */
export function createPools(config) {
  return {
    ro: mysql.createPool({ ...common, host: config.db.host, port: config.db.port, user: config.ro.user, password: config.ro.password }),
    rw: mysql.createPool({ ...common, host: config.db.host, port: config.db.port, user: config.rw.user, password: config.rw.password }),
  }
}

/** Quote an identifier. Only ever used with names taken from the catalog or from information_schema, never raw user input. */
export function quoteIdent(name) {
  return '`' + String(name).replace(/`/g, '``') + '`'
}

export function describeError(err) {
  return {
    code: err.errno ?? null,
    sqlState: err.sqlState ?? null,
    message: err.sqlMessage ?? err.message ?? String(err),
  }
}
