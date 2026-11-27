export type Role = 'EMPLOYEE' | 'PROCUREMENT_MANAGER'
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ORDERED' | 'DELIVERED'

export interface Purchase {
  id: number
  employeeUsername: string
  employeeName: string
  itemName: string
  category: string
  categoryLabel: string
  quantity: number
  estimatedUnitCost: number
  estimatedTotal: number
  justification: string
  status: RequestStatus
  submittedAt: string
  decidedBy: string | null
  decidedAt: string | null
  rejectionReason: string | null
  orderReference: string | null
  orderedAt: string | null
  deliveredAt: string | null
}

export interface PurchaseInput {
  itemName: string
  category: string
  quantity: number
  estimatedUnitCost: number
  justification: string
}

export interface CategoryOption {
  value: string
  label: string
}

export interface StatusTotal {
  status: RequestStatus
  count: number
  amount: number
}

export interface CategoryTotal {
  category: string
  categoryLabel: string
  count: number
  amount: number
}

export interface Dashboard {
  byStatus: StatusTotal[]
  monthlyBudget: number
  spentThisMonth: number
  remainingThisMonth: number
  spendByCategory: CategoryTotal[]
  oldestPendingAt: string | null
}

export interface RequestFilter {
  status?: RequestStatus
  category?: string
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
