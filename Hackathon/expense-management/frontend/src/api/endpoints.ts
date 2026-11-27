import { http } from './client'
import type {
  AuthResponse,
  CategoryOption,
  Expense,
  ExpenseFilter,
  ExpenseInput,
  ManagerDashboard,
  RegisterInput,
  StatusSummary,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const expenseApi = {
  categories: () => http.get<CategoryOption[]>('/expenses/categories').then((r) => r.data),
  submit: (input: ExpenseInput) => http.post<Expense>('/expenses', input).then((r) => r.data),
  mine: (filter: ExpenseFilter) => http.get<Expense[]>('/expenses/my', { params: filter }).then((r) => r.data),
  mySummary: () => http.get<StatusSummary>('/expenses/my/summary').then((r) => r.data),
}

export const managerApi = {
  expenses: (filter: ExpenseFilter) =>
    http.get<Expense[]>('/manager/expenses', { params: filter }).then((r) => r.data),
  approve: (id: number) => http.put<Expense>(`/manager/expenses/${id}/approve`).then((r) => r.data),
  reject: (id: number, reason: string) =>
    http.put<Expense>(`/manager/expenses/${id}/reject`, { reason }).then((r) => r.data),
  dashboard: () => http.get<ManagerDashboard>('/manager/dashboard').then((r) => r.data),
}
