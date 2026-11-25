import { toQueryParams } from '../lib/filters'
import { http } from './client'
import type {
  Account,
  AccountStatus,
  AccountType,
  AuthResponse,
  Balance,
  MoneyInput,
  Page,
  RegisterInput,
  Transaction,
  TransactionFilters,
  TransferInput,
  TransferReceipt,
} from './types'

/**
 * One function per backend endpoint (see backend controller/ package). Pure data access: no React in here.
 * Pages use these through the hooks in api/queries.ts.
 *
 * Deposit, withdraw and transfer take an idempotencyKey: the backend (service/IdempotencyService) executes a request
 * with a given key at most once, so a double click or a retry after a network error can never move money twice.
 */

const idempotent = (key: string) => ({ headers: { 'Idempotency-Key': key } })

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const accountApi = {
  create: (accountType: AccountType, initialDeposit: number) =>
    http.post<Account>('/accounts', { accountType, initialDeposit }).then((r) => r.data),
  mine: () => http.get<Account[]>('/accounts/me').then((r) => r.data),
  get: (accountNumber: string) => http.get<Account>(`/accounts/${accountNumber}`).then((r) => r.data),
  balance: (accountNumber: string) =>
    http.get<Balance>(`/accounts/${accountNumber}/balance`).then((r) => r.data),
  history: (accountNumber: string, page: number, size: number, filters: TransactionFilters = {}) =>
    http
      .get<Page<Transaction>>(`/accounts/${accountNumber}/transactions`, { params: { page, size, ...toQueryParams(filters) } })
      .then((r) => r.data),
}

export const moneyApi = {
  deposit: (accountNumber: string, input: MoneyInput, key: string) =>
    http.post<Transaction>(`/accounts/${accountNumber}/deposit`, input, idempotent(key)).then((r) => r.data),
  withdraw: (accountNumber: string, input: MoneyInput, key: string) =>
    http.post<Transaction>(`/accounts/${accountNumber}/withdraw`, input, idempotent(key)).then((r) => r.data),
  transfer: (input: TransferInput, key: string) =>
    http.post<TransferReceipt>('/transfers', input, idempotent(key)).then((r) => r.data),
}

export const adminApi = {
  accounts: () => http.get<Account[]>('/admin/accounts').then((r) => r.data),
  updateStatus: (accountNumber: string, status: AccountStatus) =>
    http.patch<Account>(`/admin/accounts/${accountNumber}/status`, { status }).then((r) => r.data),
}
