/**
 * TypeScript mirror of the backend DTOs (backend/src/main/java/com/csb/banking/dto and entity enums).
 * Keep in sync by hand: when a record in dto/ changes, change the matching interface here.
 */

export type Role = 'CUSTOMER' | 'ADMIN'
export type AccountType = 'SAVINGS' | 'CURRENT'
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER'
export type TransactionDirection = 'CREDIT' | 'DEBIT'
export type TransactionStatus = 'SUCCESS' | 'FAILED'

/** AuthResponse - answer of login and register. */
export interface AuthResponse {
  token: string
  expiresInSeconds: number
  username: string
  fullName: string
  role: Role
}

/** RegisterRequest */
export interface RegisterInput {
  username: string
  password: string
  fullName: string
  email: string
  mobile: string
  address: string
}

/** AccountResponse */
export interface Account {
  accountNumber: string
  accountType: AccountType
  status: AccountStatus
  balance: number
  customerName: string
  createdAt: string
}

/** BalanceResponse */
export interface Balance {
  accountNumber: string
  balance: number
  asOf: string
}

/** TransactionResponse */
export interface Transaction {
  transactionRef: string
  type: TransactionType
  direction: TransactionDirection
  amount: number
  status: TransactionStatus
  description: string | null
  counterpartyAccountNumber: string | null
  balanceAfter: number
  createdAt: string
}

/** TransferResponse */
export interface TransferReceipt {
  transactionRef: string
  fromAccount: string
  toAccount: string
  amount: number
  status: TransactionStatus
  fromAccountBalanceAfter: number
  createdAt: string
}

/** PageResponse<T> - page is zero-based. */
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Optional history filters; the backend's dto/TransactionFilter. Dates are YYYY-MM-DD and inclusive. */
export interface TransactionFilters {
  from?: string
  to?: string
  type?: TransactionType
  status?: TransactionStatus
  direction?: TransactionDirection
  minAmount?: number
  maxAmount?: number
}

export interface MoneyInput {
  amount: number
  description?: string
}

export interface TransferInput {
  fromAccount: string
  toAccount: string
  amount: number
  description?: string
}
