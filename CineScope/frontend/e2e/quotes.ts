import { readFileSync } from 'node:fs'
import path from 'node:path'

/**
 * The famous lines as the backend seeds them (backend/src/main/resources/seed/quotes.psv: category|type|tmdbId|title|year|speaker|line),
 * read here too so the browser tests can tell what the right answer to a question is without asking the app. Used by
 * signin-screens.spec.ts and quiz.spec.ts. Run from the frontend folder, as Playwright is.
 */
export type Seed = { category: string; type: string; id: number; title: string; year: number; speaker: string; line: string }

export const seed: Seed[] = readFileSync(path.resolve(process.cwd(), '../backend/src/main/resources/seed/quotes.psv'), 'utf8')
  .split(/\r?\n/)
  .filter((row) => row.trim() && !row.startsWith('#'))
  .map((row) => {
    const [category, type, id, title, year, speaker, ...line] = row.split('|')
    return { category, type, id: Number(id), title, year: Number(year), speaker, line: line.join('|') }
  })
