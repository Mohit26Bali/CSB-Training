export type Role = 'CUSTOMER' | 'SERVICE_AGENT'
export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'IN_REPAIR' | 'COMPLETED'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Product {
  id: number
  name: string
  brand: string
  category: string
  categoryLabel: string
  serialNumber: string
  purchaseDate: string
  warrantyMonths: number
  warrantyEnd: string
  warrantyActive: boolean
  daysLeft: number
  expiringSoon: boolean
  hasActiveClaim: boolean
}

export interface ProductInput {
  name: string
  brand: string
  category: string
  serialNumber: string
  purchaseDate: string
  warrantyMonths: number
}

export interface CategoryOption {
  value: string
  label: string
}

export interface Claim {
  id: number
  productId: number
  productName: string
  brand: string
  category: string
  categoryLabel: string
  serialNumber: string
  warrantyEnd: string
  customerUsername: string
  customerName: string
  issueDescription: string
  priority: Priority
  status: ClaimStatus
  createdAt: string
  decidedBy: string | null
  decidedAt: string | null
  rejectionReason: string | null
  repairStartedAt: string | null
  completedAt: string | null
  resolutionNote: string | null
}

export interface ClaimInput {
  productId: number
  issueDescription: string
  priority: Priority
}

export interface StatusCount {
  status: ClaimStatus
  count: number
}

export interface ClaimFilter {
  status?: ClaimStatus
  priority?: Priority
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
