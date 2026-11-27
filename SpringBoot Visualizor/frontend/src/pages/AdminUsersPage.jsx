import { useEffect, useState } from 'react'
import { listUsers, updateUserEnabled, updateUserRole } from '../api/adminApi'
import { describeError } from '../api/errors'
import { useAuth } from '../auth/useAuth'
import AppHeader from '../components/AppHeader'

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN']
const PAGE_SIZE = 10

export default function AdminUsersPage() {
  const { user: me } = useAuth()
  const [page, setPage] = useState(0)
  const [roleFilter, setRoleFilter] = useState('')
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    let cancelled = false
    listUsers({ page, size: PAGE_SIZE, role: roleFilter, q: appliedSearch })
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(describeError(err).message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [page, roleFilter, appliedSearch])

  function replaceRow(updated) {
    setData((prev) => ({ ...prev, content: prev.content.map((u) => (u.id === updated.id ? updated : u)) }))
  }

  async function mutate(target, action) {
    setBusyId(target.id)
    setError(null)
    try {
      replaceRow(await action())
    } catch (err) {
      // The server is the authority (self-protection, last-admin rule, 403 ...); show its explanation.
      setError(describeError(err).message)
    } finally {
      setBusyId(null)
    }
  }

  function changeRole(target, role) {
    if (role === target.role) return
    if (!window.confirm(`Change ${target.username} from ${target.role} to ${role}?`)) return
    mutate(target, () => updateUserRole(target.id, role))
  }

  function toggleEnabled(target) {
    const verb = target.enabled ? 'Disable' : 'Enable'
    if (!window.confirm(`${verb} account ${target.username}?`)) return
    mutate(target, () => updateUserEnabled(target.id, !target.enabled))
  }

  function applySearch(event) {
    event.preventDefault()
    setLoading(true)
    setPage(0)
    setAppliedSearch(search.trim())
  }

  function changeRoleFilter(event) {
    setLoading(true)
    setPage(0)
    setRoleFilter(event.target.value)
  }

  function goToPage(next) {
    setLoading(true)
    setPage(next)
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-5xl p-4 sm:p-6">
        <h1 className="text-2xl font-bold">User management</h1>

        <div className="mt-4 flex flex-wrap items-end gap-3">
          <form onSubmit={applySearch} className="flex gap-2">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search username or email"
              aria-label="Search users"
              className="rounded border border-line-strong px-3 py-1.5 text-sm"
            />
            <button className="rounded bg-accent px-3 py-1.5 text-sm text-on-accent hover:bg-accent-hover">Search</button>
          </form>
          <label className="text-sm">
            Role{' '}
            <select value={roleFilter} onChange={changeRoleFilter} className="rounded-md border border-line-strong px-2 py-1.5.5">
              <option value="">All</option>
              {ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
        </div>

        {error && (
          <p role="alert" className="mt-4 rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
            {error}
          </p>
        )}

        <div className="mt-4 overflow-x-auto rounded-lg border border-line bg-surface shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="bg-subtle text-fg-2">
              <tr>
                <th className="px-3 py-2">Username</th>
                <th className="px-3 py-2">Email</th>
                <th className="px-3 py-2">Role</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Created</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={5} className="px-3 py-4 text-muted">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && data?.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-4 text-muted">
                    No users match.
                  </td>
                </tr>
              )}
              {!loading &&
                data?.content.map((u) => {
                  const isSelf = u.id === me.id
                  const busy = busyId === u.id
                  return (
                    <tr key={u.id} className="border-t border-line-subtle">
                      <td className="px-3 py-2 font-medium">
                        {u.username} {isSelf && <span className="text-xs text-muted">(you)</span>}
                      </td>
                      <td className="px-3 py-2">{u.email}</td>
                      <td className="px-3 py-2">
                        <select
                          value={u.role}
                          disabled={busy || isSelf}
                          title={isSelf ? 'You cannot change your own role' : undefined}
                          aria-label={`Role of ${u.username}`}
                          onChange={(e) => changeRole(u, e.target.value)}
                          className="rounded-md border border-line-strong px-2 py-1.5 disabled:opacity-60"
                        >
                          {ROLES.map((r) => (
                            <option key={r}>{r}</option>
                          ))}
                        </select>
                      </td>
                      <td className="px-3 py-2">
                        <button
                          onClick={() => toggleEnabled(u)}
                          disabled={busy || isSelf}
                          title={isSelf ? 'You cannot disable your own account' : undefined}
                          className={`rounded px-2 py-1 text-xs font-medium disabled:opacity-60 ${
                            u.enabled ? 'bg-success-soft text-success' : 'bg-error-soft text-error'
                          }`}
                        >
                          {u.enabled ? 'Active - click to disable' : 'Disabled - click to enable'}
                        </button>
                      </td>
                      <td className="px-3 py-2 text-muted">{new Date(u.createdAt).toLocaleDateString()}</td>
                    </tr>
                  )
                })}
            </tbody>
          </table>
        </div>

        {data && (
          <div className="mt-3 flex items-center justify-between text-sm text-fg-2">
            <span>
              {data.totalElements} user(s) - page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => goToPage(page - 1)}
                disabled={page === 0}
                className="rounded border border-line-strong px-3 py-1 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => goToPage(page + 1)}
                disabled={page + 1 >= data.totalPages}
                className="rounded border border-line-strong px-3 py-1 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
