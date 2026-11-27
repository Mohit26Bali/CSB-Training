export type Role = 'MANAGER' | 'EMPLOYEE'
export type ExpenseStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Expense {
  id: number
  employeeUsername: string
  employeeName: string
  expenseDate: string
  category: string
  categoryLabel: string
  amount: number
  description: string
  status: ExpenseStatus
  submittedAt: string
  decidedBy: string | null
  decidedAt: string | null
  rejectionReason: string | null
}

export interface ExpenseInput {
  expenseDate: string
  category: string
  amount: number
  description: string
}

export interface CategoryOption {
  value: string
  label: string
}

export interface StatusSummary {
  pendingCount: number
  pendingAmount: number
  approvedCount: number
  approvedAmount: number
  rejectedCount: number
  rejectedAmount: number
}

export interface CategoryTotal {
  category: string
  categoryLabel: string
  count: number
  amount: number
}

export interface ManagerDashboard {
  summary: StatusSummary
  approvedByCategory: CategoryTotal[]
  oldestPendingAt: string | null
}

export interface AuthResponse {
  token: string
  expiresIn: number
  username: string
  fullname: string
  role: Role
}

export interface RegisterInput {
  username: string
  password: string
  fullname: string
}

export interface ExpenseFilter {
  status?: ExpenseStatus
  category?: string
  search?: string
}
