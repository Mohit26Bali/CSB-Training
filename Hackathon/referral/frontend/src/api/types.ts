export type Role = 'EMPLOYEE' | 'RECRUITER'
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type ReferralStatus = 'SUBMITTED' | 'SCREENING' | 'INTERVIEW' | 'SELECTED' | 'REJECTED'

export interface Job {
  id: number
  title: string
  department: string
  location: string
  description: string
  skills: string[]
  status: JobStatus
  createdBy: string
  createdAt: string
  publishedAt: string | null
  referralCount: number
}

export interface JobInput {
  title: string
  department: string
  location: string
  description: string
  skills: string[]
}

export interface Referral {
  id: number
  jobId: number
  jobTitle: string
  jobDepartment: string
  referrerUsername: string
  referrerName: string
  candidateName: string
  candidateEmail: string
  candidatePhone: string | null
  resumeUrl: string | null
  notes: string | null
  status: ReferralStatus
  allowedNext: ReferralStatus[]
  submittedAt: string
  updatedBy: string | null
  updatedAt: string | null
  statusNote: string | null
}

export interface ReferralInput {
  jobId: number
  candidateName: string
  candidateEmail: string
  candidatePhone: string
  resumeUrl: string
  notes: string
}

export interface StatusCount {
  status: ReferralStatus
  count: number
}

export interface JobStats {
  jobId: number
  title: string
  department: string
  jobStatus: JobStatus
  total: number
  inProgress: number
  selected: number
  rejected: number
}

export interface Dashboard {
  openJobs: number
  totalReferrals: number
  hires: number
  byStatus: StatusCount[]
  byJob: JobStats[]
}

export interface ReferralFilter {
  jobId?: number
  status?: ReferralStatus
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
