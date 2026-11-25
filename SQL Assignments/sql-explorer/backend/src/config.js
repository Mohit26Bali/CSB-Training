// All settings come from environment variables (see ../.env.example). There are NO default passwords.
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))

export function loadConfig(env = process.env) {
  const missing = ['APP_RO_PASSWORD', 'APP_RW_PASSWORD'].filter((k) => env[k] === undefined || env[k] === '')
  if (missing.length) {
    throw new Error(`Missing required environment variable(s): ${missing.join(', ')}. Copy .env.example to .env and fill them in.`)
  }
  const prefix = env.DB_PREFIX ?? 'sx_'
  if (!/^[a-z][a-z0-9_]*$/.test(prefix)) {
    throw new Error('DB_PREFIX must be lower-case letters, digits and underscores, starting with a letter (default "sx_").')
  }
  return {
    port: Number(env.PORT ?? 4100),
    host: env.HOST ?? '127.0.0.1', // localhost only unless you deliberately change it
    db: { host: env.DB_HOST ?? '127.0.0.1', port: Number(env.DB_PORT ?? 3306) },
    ro: { user: env.APP_RO_USER ?? 'sqlexplorer_ro', password: env.APP_RO_PASSWORD },
    rw: { user: env.APP_RW_USER ?? 'sqlexplorer_rw', password: env.APP_RW_PASSWORD },
    prefix,
    assignmentsDir: resolve(env.ASSIGNMENTS_DIR ?? resolve(here, '..', '..', '..')),
    maxRows: Number(env.MAX_ROWS ?? 500),
    rateLimitPerMinute: Number(env.RATE_LIMIT_PER_MINUTE ?? 600), // per client address, on /api
  }
}
