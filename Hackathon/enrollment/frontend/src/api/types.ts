export type Role = 'ADMIN' | 'EMPLOYEE'
export type EnrollmentStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Course {
  id: number
  title: string
  description: string | null
  category: string
  imageUrl: string | null
  capacity: number
  active: boolean
  approvedCount: number
  pendingCount: number
}

export interface CourseInput {
  title: string
  description: string
  category: string
  imageUrl: string
  capacity: number
  active: boolean
}

export interface Enrollment {
  id: number
  courseId: number
  courseTitle: string
  employeeUsername: string
  employeeName: string
  status: EnrollmentStatus
  requestedAt: string
  decidedBy: string | null
  decidedAt: string | null
}

export interface AuthResponse {
  token: string
  expiresIn: number
  username: string
  fullname: string
  role: Role
}

export interface Dashboard {
  totalCourses: number
  activeCourses: number
  totalEmployees: number
  pending: number
  approved: number
  rejected: number
  courses: Course[]
}

export interface MessageResponse {
  message: string
}

export interface RegisterInput {
  username: string
  password: string
  fullname: string
}
