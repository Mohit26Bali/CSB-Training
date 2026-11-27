export type Role = 'MEMBER' | 'LIBRARIAN'

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

export type LoanStatus = 'RESERVED' | 'ISSUED' | 'RETURNED' | 'CANCELLED'

export interface Book {
  id: number
  title: string
  author: string
  isbn: string
  category: string
  description: string | null
  publishedYear: number | null
  totalCopies: number
  availableCopies: number
  myLoanStatus: LoanStatus | null
  myLoanId: number | null
}

export interface BookInput {
  title: string
  author: string
  isbn: string
  category: string
  description: string
  publishedYear: number | ''
  totalCopies: number | ''
}

export interface Loan {
  id: number
  bookId: number
  bookTitle: string
  bookAuthor: string
  category: string
  memberUsername: string
  memberName: string
  status: LoanStatus
  reservedAt: string
  issuedAt: string | null
  dueDate: string | null
  returnedAt: string | null
  overdue: boolean
  daysOverdue: number
  fine: number
  issuedBy: string | null
  returnedBy: string | null
}

export interface CategoryCount {
  category: string
  titles: number
  copies: number
  available: number
}

export interface Dashboard {
  titles: number
  copies: number
  available: number
  reserved: number
  issued: number
  overdue: number
  finesCollected: number
  byCategory: CategoryCount[]
  overdueLoans: Loan[]
}
