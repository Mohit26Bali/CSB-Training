// Everything the browser knows about the backend: the shapes of the answers and one small fetch wrapper.

export interface AssignmentSummary {
  id: string
  title: string
  category: 'DDL' | 'DML' | 'SELECT' | 'Capstone'
  db: string
  originalDb: string
  tables: number
  rows: number
  queries: number
  negatives: number
  dml: number
}

export interface Column {
  name: string
  type: string
  nullable: boolean
  key: string
  defaultValue: string | null
  extra: string
}
export interface ForeignKey {
  column: string
  refTable: string
  refColumn: string
  name: string
}
export interface TableInfo {
  name: string
  rowCount: number
  columns: Column[]
  foreignKeys: ForeignKey[]
  checks: { name: string; clause: string }[]
}
export interface QueryDef {
  id: string
  title: string
  sql: string
  file: string
}
export interface NegativeDef {
  id: string
  title: string
  expectedCode: number
  sql: string
}
export type ScriptKind = 'schema' | 'seed' | 'alter' | 'queries' | 'dml' | 'negative' | 'other'
export interface ScriptInfo {
  file: string
  kind: ScriptKind
  source: string
  statements: number
}
export interface AssignmentDetail {
  id: string
  folder: string
  title: string
  category: string
  db: string
  originalDb: string
  readme: string
  scripts: ScriptInfo[]
  seedFiles: string[]
  dmlScripts: string[]
  queries: QueryDef[]
  negatives: NegativeDef[]
  tables: TableInfo[]
}

export type Cell = string | number | null
export interface RowsResult {
  kind: 'rows'
  columns: string[]
  rows: Cell[][]
  totalRows: number
  truncated: boolean
  ms?: number
}
export interface ChangeResult {
  kind: 'change'
  affectedRows: number
  changedRows: number | null
  insertId: number | null
  info: string
}
export interface ErrorResult {
  kind: 'error'
  error: { code: number | null; message: string; sqlState?: string | null }
  ms?: number
}
export type StatementResult = RowsResult | ChangeResult | ErrorResult | { kind: 'transaction'; note: string } | { kind: 'setting' }
export type SandboxStep = StatementResult & { sql: string; comments: string[]; line: number; file: string }

export interface TablePage {
  table: string
  columns: string[]
  rows: Cell[][]
  page: number
  pageSize: number
  totalRows: number
  totalPages: number
}

export interface NegativeResult {
  id: string
  title: string
  sql: string
  expectedCode: number
  actualCode: number | null
  message: string | null
  unexpectedSuccess: boolean
  ok: boolean
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(response.status, body?.error ?? `The server answered ${response.status}.`)
  return body as T
}

const post = <T,>(path: string, body: unknown = {}) => request<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const api = {
  assignments: () => request<AssignmentSummary[]>('/api/assignments'),
  assignment: (id: string) => request<AssignmentDetail>(`/api/assignments/${encodeURIComponent(id)}`),
  table: (id: string, table: string, page: number, pageSize = 15) =>
    request<TablePage>(`/api/assignments/${encodeURIComponent(id)}/tables/${encodeURIComponent(table)}?page=${page}&pageSize=${pageSize}`),
  runQuery: (id: string, queryId: string, mode: 'seed' | 'after-scripts') =>
    post<StatementResult & { mode: string }>(`/api/assignments/${encodeURIComponent(id)}/queries/${encodeURIComponent(queryId)}/run`, { mode }),
  runScript: (id: string, file: string) =>
    post<{ file: string; appliedBefore: string[]; steps: SandboxStep[]; rolledBack: boolean }>(
      `/api/assignments/${encodeURIComponent(id)}/scripts/${encodeURIComponent(file)}/run`,
    ),
  runNegatives: (id: string) =>
    post<{ results: NegativeResult[]; passed: number; total: number }>(`/api/assignments/${encodeURIComponent(id)}/negatives/run`),
  runSql: (id: string, sql: string) => post<StatementResult>(`/api/assignments/${encodeURIComponent(id)}/sql`, { sql }),
}
