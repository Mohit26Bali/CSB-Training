import { http } from './client'
import type {
  AuthResponse,
  Dashboard,
  Job,
  JobInput,
  JobStatus,
  Referral,
  ReferralFilter,
  ReferralInput,
  ReferralStatus,
  RegisterInput,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const jobApi = {
  open: (search?: string, skill?: string) => http.get<Job[]>('/jobs', { params: { search, skill } }).then((r) => r.data),
  skills: () => http.get<string[]>('/jobs/skills').then((r) => r.data),
}

export const referralApi = {
  refer: (input: ReferralInput) => http.post<Referral>('/referrals', input).then((r) => r.data),
  mine: (status?: ReferralStatus) => http.get<Referral[]>('/referrals/my', { params: { status } }).then((r) => r.data),
}

export const recruiterApi = {
  jobs: (status?: JobStatus) => http.get<Job[]>('/recruiter/jobs', { params: { status } }).then((r) => r.data),
  createJob: (input: JobInput) => http.post<Job>('/recruiter/jobs', input).then((r) => r.data),
  updateJob: (id: number, input: JobInput) => http.put<Job>(`/recruiter/jobs/${id}`, input).then((r) => r.data),
  publish: (id: number) => http.put<Job>(`/recruiter/jobs/${id}/publish`).then((r) => r.data),
  close: (id: number) => http.put<Job>(`/recruiter/jobs/${id}/close`).then((r) => r.data),
  referrals: (filter: ReferralFilter) => http.get<Referral[]>('/recruiter/referrals', { params: filter }).then((r) => r.data),
  move: (id: number, status: ReferralStatus, note?: string) =>
    http.put<Referral>(`/recruiter/referrals/${id}/status`, { status, note }).then((r) => r.data),
  dashboard: () => http.get<Dashboard>('/recruiter/dashboard').then((r) => r.data),
}
