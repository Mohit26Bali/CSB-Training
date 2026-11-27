import { http } from './client'
import type { AuthResponse, Book, BookInput, Dashboard, Loan, LoanStatus, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const bookApi = {
  search: (search?: string, category?: string, availableOnly?: boolean) =>
    http.get<Book[]>('/books', { params: { search, category, availableOnly } }).then((r) => r.data),
  categories: () => http.get<string[]>('/books/categories').then((r) => r.data),
}

export const loanApi = {
  reserve: (bookId: number) => http.post<Loan>('/loans', { bookId }).then((r) => r.data),
  mine: (status?: LoanStatus) => http.get<Loan[]>('/loans/my', { params: { status } }).then((r) => r.data),
  cancel: (id: number) => http.delete<Loan>(`/loans/${id}`).then((r) => r.data),
}

const bookBody = (input: BookInput) => ({
  ...input,
  publishedYear: input.publishedYear === '' ? null : Number(input.publishedYear),
  totalCopies: input.totalCopies === '' ? null : Number(input.totalCopies),
})

export const librarianApi = {
  loans: (status?: LoanStatus, search?: string) => http.get<Loan[]>('/librarian/loans', { params: { status, search } }).then((r) => r.data),
  issue: (id: number) => http.put<Loan>(`/librarian/loans/${id}/issue`).then((r) => r.data),
  giveBack: (id: number) => http.put<Loan>(`/librarian/loans/${id}/return`).then((r) => r.data),
  createBook: (input: BookInput) => http.post<Book>('/librarian/books', bookBody(input)).then((r) => r.data),
  updateBook: (id: number, input: BookInput) => http.put<Book>(`/librarian/books/${id}`, bookBody(input)).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/librarian/dashboard').then((r) => r.data),
}
