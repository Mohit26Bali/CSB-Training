export type Role = 'STUDENT' | 'SCHOLARSHIP_ADMIN'
export type ApplicationStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface Program {
  id: number
  name: string
  description: string | null
  amount: number
  deadline: string
  minGpa: number
  maxFamilyIncome: number
  active: boolean
  open: boolean
  applicationCount: number
  approvedCount: number
}

export interface ProgramInput {
  name: string
  description: string
  amount: number
  deadline: string
  minGpa: number
  maxFamilyIncome: number
  active: boolean
}

export interface Application {
  id: number
  programId: number
  programName: string
  programAmount: number
  studentUsername: string
  studentName: string
  gpa: number
  annualFamilyIncome: number
  course: string
  yearOfStudy: number
  statement: string
  eligibilityScore: number
  meetsCriteria: boolean
  status: ApplicationStatus
  submittedAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
}

export interface ApplicationInput {
  programId: number
  gpa: number
  annualFamilyIncome: number
  course: string
  yearOfStudy: number
  statement: string
}

export interface ProgramStats {
  programId: number
  name: string
  total: number
  submitted: number
  approved: number
  rejected: number
  averageScore: number
  awardedAmount: number
}

export interface Statistics {
  totalApplications: number
  submitted: number
  approved: number
  rejected: number
  approvalRate: number
  totalAwarded: number
  byProgram: ProgramStats[]
}

export interface ApplicationFilter {
  programId?: number
  status?: ApplicationStatus
  search?: string
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
