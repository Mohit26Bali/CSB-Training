import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAdminUsers, useDeleteAdminUser } from '../api/queries'
import type { AdminUser } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { AdminTabs } from '../components/AdminTabs'
import { AdminUserForm } from '../components/AdminUserForm'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Button, EmptyState, ErrorState, Skeleton } from '../components/ui'
import { useDebounced, useDocumentTitle } from '../hooks'
import { formatDate } from '../lib/format'
import { regionName } from '../lib/region'

/**
 * "Manage accounts" (route /admin/users, administrators only - auth/RequireAdmin): every account in a table with search, role
 * and status filters and paging, plus create, edit and delete. The whole create / read / update / delete of accounts is here;
 * the data comes from GET /api/admin/users (backend AdminUserController), the changes go through components/AdminUserForm
 * and the delete dialog below. The filters and the page live in the address (?q=&role=&enabled=&page=), so a reload or a shared
 * link shows the same view. The administrator's own row cannot be deleted from here, and its role and "enabled" are locked in the
 * edit form - the same rules the server enforces.
 */
export function AdminUsersPage() {
  useDocumentTitle('Manage accounts')
  const { session } = useAuth()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const role = (['USER', 'ADMIN'] as const).find((r) => r === params.get('role')) ?? null
  const enabled = params.get('enabled') === 'true' ? true : params.get('enabled') === 'false' ? false : null
  const page = Math.max(1, Number(params.get('page')) || 1)

  const [search, setSearch] = useState(q)
  const debounced = useDebounced(search, 300)
  const [editing, setEditing] = useState<AdminUser | 'new' | null>(null)
  const [deleting, setDeleting] = useState<AdminUser | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const list = useAdminUsers({ q, role, enabled, page })
  const remove = useDeleteAdminUser()

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  // the search box types freely; the address (and so the request) follows once typing pauses
  useEffect(() => {
    if (debounced.trim() !== q) update({ q: debounced.trim() || null, page: null })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  async function confirmDelete() {
    if (!deleting) return
    try {
      await remove.mutateAsync(deleting.id)
      setNotice(`Account ${deleting.username} was deleted.`)
      setDeleting(null)
    } catch {
      /* shown in the dialog from remove.error */
    }
  }

  const select = 'h-10 rounded-xl border border-line bg-surface px-3 text-sm text-ink focus:border-ember-400 focus:outline-none'
  const data = list.data

  return (
    <div className="mx-auto max-w-7xl px-4 pb-10 pt-28 sm:px-8">
      <AdminTabs />
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">Manage accounts</h1>
          <p className="mt-1 text-sm text-muted">Everyone who has an account. Only administrators can see this page.</p>
        </div>
        <Button onClick={() => setEditing('new')}>New account</Button>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3" role="search" aria-label="Filter accounts">
        <label className="sr-only" htmlFor="admin-search">
          Search accounts
        </label>
        <input
          id="admin-search"
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name, username or email"
          autoComplete="off"
          className="h-10 w-full rounded-xl border border-line bg-surface px-4 text-sm text-ink placeholder:text-faint focus:border-ember-400 focus:outline-none sm:w-80"
        />
        <label className="flex items-center gap-2 text-sm text-muted">
          Role
          <select value={role ?? ''} onChange={(e) => update({ role: e.target.value || null, page: null })} className={select}>
            <option value="">All</option>
            <option value="USER">Users</option>
            <option value="ADMIN">Administrators</option>
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm text-muted">
          Status
          <select value={enabled === null ? '' : String(enabled)} onChange={(e) => update({ enabled: e.target.value || null, page: null })} className={select}>
            <option value="">All</option>
            <option value="true">Enabled</option>
            <option value="false">Disabled</option>
          </select>
        </label>
      </div>

      {notice && (
        <p role="status" className="mt-5 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-ink">
          {notice}
        </p>
      )}

      <div className="mt-6">
        {list.isLoading ? (
          <div aria-busy="true" className="space-y-2">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : list.isError ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
        ) : data!.items.length === 0 ? (
          <EmptyState title="No accounts match" hint="Try a different search or clear the filters." />
        ) : (
          <>
            <div className="overflow-x-auto rounded-2xl border border-line">
              <table className="w-full min-w-[56rem] text-left text-sm">
                <caption className="sr-only">Accounts</caption>
                <thead className="bg-surface-2 text-xs uppercase tracking-wide text-faint">
                  <tr>
                    <th scope="col" className="px-4 py-3">Account</th>
                    <th scope="col" className="px-4 py-3">Role</th>
                    <th scope="col" className="px-4 py-3">Status</th>
                    <th scope="col" className="px-4 py-3">Country</th>
                    <th scope="col" className="px-4 py-3 text-right">Watchlist</th>
                    <th scope="col" className="px-4 py-3">Joined</th>
                    <th scope="col" className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line bg-surface">
                  {data!.items.map((u) => {
                    const self = u.username === session?.username
                    return (
                      <tr key={u.id} data-username={u.username}>
                        <td className="px-4 py-3">
                          <p className="font-semibold text-ink">
                            {u.displayName} {self && <span className="ml-1 text-xs font-normal text-faint">(you)</span>}
                          </p>
                          <p className="text-xs text-faint">
                            @{u.username} · {u.email}
                          </p>
                        </td>
                        <td className="px-4 py-3">
                          <Badge tone={u.role === 'ADMIN' ? 'ember' : 'plain'}>{u.role === 'ADMIN' ? 'Administrator' : 'User'}</Badge>
                        </td>
                        <td className="px-4 py-3">
                          <Badge tone={u.enabled ? 'good' : 'bad'}>{u.enabled ? 'Enabled' : 'Disabled'}</Badge>
                        </td>
                        <td className="px-4 py-3 text-muted">{regionName(u.region)}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-ink">{u.watchlistCount}</td>
                        <td className="px-4 py-3 text-muted">{formatDate(u.createdAt.slice(0, 10))}</td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-2">
                            <Button variant="secondary" className="px-3 py-1.5" aria-label={`Edit ${u.username}`} onClick={() => setEditing(u)}>
                              Edit
                            </Button>
                            <Button
                              variant="ghost"
                              className="px-3 py-1.5"
                              aria-label={`Delete ${u.username}`}
                              disabled={self}
                              title={self ? 'You cannot delete your own account here' : undefined}
                              onClick={() => {
                                remove.reset()
                                setDeleting(u)
                              }}
                            >
                              Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
              <p>
                Showing {data!.items.length} of {data!.totalResults} account{data!.totalResults === 1 ? '' : 's'}
              </p>
              {data!.totalPages > 1 && (
                <nav aria-label="Pages" className="flex items-center gap-3">
                  <Button variant="secondary" disabled={page <= 1} onClick={() => update({ page: page > 2 ? String(page - 1) : null })}>
                    Previous
                  </Button>
                  <span>
                    Page {data!.page} of {data!.totalPages}
                  </span>
                  <Button variant="secondary" disabled={page >= data!.totalPages} onClick={() => update({ page: String(page + 1) })}>
                    Next
                  </Button>
                </nav>
              )}
            </div>
          </>
        )}
      </div>

      {editing && (
        <AdminUserForm
          user={editing === 'new' ? null : editing}
          isSelf={editing !== 'new' && editing.username === session?.username}
          onClose={() => setEditing(null)}
          onDone={(message) => {
            setEditing(null)
            setNotice(message)
          }}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.username ?? 'account'}?`}
        confirmLabel="Delete account"
        busy={remove.isPending}
        error={remove.isError ? errorMessage(remove.error) : null}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      >
        <p className="text-sm text-ink">
          This permanently deletes <strong>{deleting?.displayName}</strong> (@{deleting?.username}) and their {deleting?.watchlistCount ?? 0} watchlist{' '}
          {deleting?.watchlistCount === 1 ? 'entry' : 'entries'}. It cannot be undone.
        </p>
      </ConfirmDialog>
    </div>
  )
}

function Badge({ tone, children }: { tone: 'plain' | 'ember' | 'good' | 'bad'; children: React.ReactNode }) {
  const tones = {
    plain: 'border-line text-muted',
    ember: 'border-ember-500/40 bg-ember-500/15 text-ember-400',
    good: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-500',
    bad: 'border-rose-500/40 bg-rose-500/10 text-rose-400',
  }
  return <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>
}

