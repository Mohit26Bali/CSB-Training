import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { accountApi, adminApi, moneyApi } from './endpoints'
import type { AccountStatus, AccountType, MoneyInput, TransactionFilters, TransferInput } from './types'

/**
 * React Query hooks: the only way pages read or change server data.
 * All keys start with 'bank', so after any money movement a single invalidate refreshes every balance, account list
 * and history on screen (see useRefreshBank).
 */

const ROOT = ['bank'] as const

export const keys = {
  myAccounts: [...ROOT, 'accounts', 'mine'] as const,
  account: (no: string) => [...ROOT, 'account', no] as const,
  history: (no: string, page: number, size: number, filters: TransactionFilters = {}) =>
    [...ROOT, 'history', no, page, size, filters] as const,
  adminAccounts: [...ROOT, 'admin', 'accounts'] as const,
}

export function useRefreshBank() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ROOT })
}

export const useMyAccounts = () => useQuery({ queryKey: keys.myAccounts, queryFn: accountApi.mine })

export const useAccount = (accountNumber: string | undefined) =>
  useQuery({
    queryKey: keys.account(accountNumber ?? ''),
    queryFn: () => accountApi.get(accountNumber!),
    enabled: !!accountNumber,
  })

export const useHistory = (accountNumber: string | undefined, page: number, size = 10, filters: TransactionFilters = {}) =>
  useQuery({
    queryKey: keys.history(accountNumber ?? '', page, size, filters),
    queryFn: () => accountApi.history(accountNumber!, page, size, filters),
    enabled: !!accountNumber,
    placeholderData: keepPreviousData,
  })

/** The latest few transactions of several accounts at once (dashboard "recent activity"). */
export function useRecentActivity(accountNumbers: string[], size = 5) {
  return useQueries({
    queries: accountNumbers.map((no) => ({
      queryKey: keys.history(no, 0, size),
      queryFn: () => accountApi.history(no, 0, size),
    })),
  })
}

export const useAdminAccounts = () => useQuery({ queryKey: keys.adminAccounts, queryFn: adminApi.accounts })

export function useCreateAccount() {
  const refresh = useRefreshBank()
  return useMutation({
    mutationFn: (v: { accountType: AccountType; initialDeposit: number }) =>
      accountApi.create(v.accountType, v.initialDeposit),
    onSuccess: refresh,
  })
}

export function useDeposit(accountNumber: string) {
  const refresh = useRefreshBank()
  return useMutation({
    mutationFn: (v: { input: MoneyInput; key: string }) => moneyApi.deposit(accountNumber, v.input, v.key),
    onSuccess: refresh,
  })
}

export function useWithdraw(accountNumber: string) {
  const refresh = useRefreshBank()
  return useMutation({
    mutationFn: (v: { input: MoneyInput; key: string }) => moneyApi.withdraw(accountNumber, v.input, v.key),
    onSuccess: refresh,
  })
}

export function useTransfer() {
  const refresh = useRefreshBank()
  return useMutation({
    mutationFn: (v: { input: TransferInput; key: string }) => moneyApi.transfer(v.input, v.key),
    onSuccess: refresh,
  })
}

export function useUpdateAccountStatus() {
  const refresh = useRefreshBank()
  return useMutation({
    mutationFn: (v: { accountNumber: string; status: AccountStatus }) =>
      adminApi.updateStatus(v.accountNumber, v.status),
    onSuccess: refresh,
  })
}
