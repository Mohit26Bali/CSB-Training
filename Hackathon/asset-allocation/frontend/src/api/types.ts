export type Role = 'EMPLOYEE' | 'ASSET_ADMIN'

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

export type Category = 'LAPTOP' | 'MONITOR' | 'PHONE' | 'TABLET' | 'ACCESSORY' | 'OTHER'
export type Condition = 'NEW' | 'GOOD' | 'FAIR' | 'POOR'
export type AssetStatus = 'AVAILABLE' | 'ASSIGNED' | 'RETURN_REQUESTED' | 'IN_REPAIR' | 'RETIRED'

export interface Asset {
  id: number
  assetTag: string
  serialNumber: string
  name: string
  category: Category
  brand: string | null
  model: string | null
  purchaseDate: string | null
  purchasePrice: number | null
  condition: Condition
  status: AssetStatus
  notes: string | null
  assignmentId: number | null
  holderName: string | null
  holderUsername: string | null
  heldSince: string | null
}

export interface AssetInput {
  assetTag: string
  serialNumber: string
  name: string
  category: string
  brand: string
  model: string
  purchaseDate: string
  purchasePrice: string | number
  condition: string
  notes: string
}

export interface Assignment {
  id: number
  assetId: number
  assetTag: string
  assetName: string
  category: Category
  serialNumber: string
  employeeName: string
  employeeUsername: string
  assignedAt: string
  assignedBy: string
  dueDate: string | null
  conditionOut: Condition
  note: string | null
  status: 'ASSIGNED' | 'RETURN_REQUESTED' | 'RETURNED'
  returnRequestedAt: string | null
  returnNote: string | null
  returnedAt: string | null
  receivedBy: string | null
  conditionIn: Condition | null
  receiveNote: string | null
  overdue: boolean
  canRequestReturn: boolean
}

export interface Employee {
  id: number
  username: string
  fullname: string
  activeAssets: number
}

export interface Dashboard {
  totalAssets: number
  available: number
  assigned: number
  returnRequests: number
  inRepair: number
  retired: number
  overdue: number
  poorCondition: number
  assetValue: number
  byCategory: { key: string; count: number }[]
  byStatus: { key: string; count: number }[]
  oldestRequests: Assignment[]
}
