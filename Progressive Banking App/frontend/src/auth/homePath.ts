import type { Role } from '../api/types'

/** Where each role lands after login (and after "Go home" links). Used by auth/RequireRole, App and the auth pages. */
export function homePathFor(role: Role): string {
  return role === 'ADMIN' ? '/admin' : '/dashboard'
}
