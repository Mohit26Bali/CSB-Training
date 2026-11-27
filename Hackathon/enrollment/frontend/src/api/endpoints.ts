import { http } from './client'
import type {
  AuthResponse,
  Course,
  CourseInput,
  Dashboard,
  Enrollment,
  EnrollmentStatus,
  MessageResponse,
  RegisterInput,
} from './types'

export interface CourseFilter {
  search?: string
  category?: string
}

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}

export const courseApi = {
  browse: (filter: CourseFilter) => http.get<Course[]>('/courses', { params: filter }).then((r) => r.data),
  categories: () => http.get<string[]>('/courses/categories').then((r) => r.data),
}

export const enrollmentApi = {
  request: (courseId: number) => http.post<Enrollment>('/enrollments', { courseId }).then((r) => r.data),
  mine: () => http.get<Enrollment[]>('/enrollments/my').then((r) => r.data),
  cancel: (id: number) => http.delete<MessageResponse>(`/enrollments/${id}`).then((r) => r.data),
}

export const adminApi = {
  dashboard: () => http.get<Dashboard>('/admin/dashboard').then((r) => r.data),
  courses: (filter: CourseFilter) => http.get<Course[]>('/admin/courses', { params: filter }).then((r) => r.data),
  createCourse: (input: CourseInput) => http.post<Course>('/admin/courses', input).then((r) => r.data),
  updateCourse: (id: number, input: CourseInput) =>
    http.put<Course>(`/admin/courses/${id}`, input).then((r) => r.data),
  deleteCourse: (id: number) => http.delete<MessageResponse>(`/admin/courses/${id}`).then((r) => r.data),
  rejectPending: (courseId: number) =>
    http.post<MessageResponse>(`/admin/courses/${courseId}/reject-pending`).then((r) => r.data),
  enrollments: (status?: EnrollmentStatus) =>
    http.get<Enrollment[]>('/admin/enrollments', { params: { status } }).then((r) => r.data),
  approve: (id: number) => http.put<Enrollment>(`/admin/enrollments/${id}/approve`).then((r) => r.data),
  reject: (id: number) => http.put<Enrollment>(`/admin/enrollments/${id}/reject`).then((r) => r.data),
}
