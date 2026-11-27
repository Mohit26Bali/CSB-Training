import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { adminApi, courseApi } from '../../api/endpoints'
import type { Course } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, ConfirmDialog, CourseImage, EmptyState, ErrorState, LoadingState, PageHeader, SeatBar, Tag } from '../../components/ui'
import { useDebounced } from '../../hooks'
import { CourseFormModal } from './CourseFormModal'

type Pending =
  | { kind: 'delete'; course: Course }
  | { kind: 'reject-pending'; course: Course }
  | { kind: 'toggle'; course: Course }

export function CoursesPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const debouncedSearch = useDebounced(search)
  const [editing, setEditing] = useState<Course | 'new' | null>(null)
  const [confirm, setConfirm] = useState<Pending | null>(null)

  const courses = useQuery({
    queryKey: ['admin', 'courses', debouncedSearch, category],
    queryFn: () => adminApi.courses({ search: debouncedSearch, category }),
    placeholderData: (previous) => previous,
  })
  const categories = useQuery({ queryKey: ['categories'], queryFn: courseApi.categories })

  const act = useMutation({
    mutationFn: async (p: Pending) => {
      if (p.kind === 'delete') return (await adminApi.deleteCourse(p.course.id)).message
      if (p.kind === 'reject-pending') return (await adminApi.rejectPending(p.course.id)).message
      const c = p.course
      await adminApi.updateCourse(c.id, {
        title: c.title,
        description: c.description ?? '',
        category: c.category,
        imageUrl: c.imageUrl ?? '',
        capacity: c.capacity,
        active: !c.active,
      })
      return c.active ? `“${c.title}” is now closed for enrollment` : `“${c.title}” is now open for enrollment`
    },
    onSuccess: (message) => toast.success(message),
    onError: (err) => toast.error(errorMessage(err)),
    onSettled: () => {
      setConfirm(null)
      return queryClient.invalidateQueries()
    },
  })

  const filtered = search !== '' || category !== ''

  return (
    <>
      <PageHeader
        title="Courses"
        subtitle="Create and manage the training catalogue."
        actions={<Button onClick={() => setEditing('new')}>New course</Button>}
      />

      <div className="mb-6 flex flex-wrap gap-3">
        <div className="min-w-56 flex-1">
          <label htmlFor="search" className="sr-only">
            Search courses
          </label>
          <input
            id="search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title or description"
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="category" className="sr-only">
            Filter by category
          </label>
          <select
            id="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm sm:w-auto"
          >
            <option value="">All categories</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
      </div>

      {courses.isPending ? (
        <LoadingState label="Loading courses…" />
      ) : courses.isError ? (
        <ErrorState message={errorMessage(courses.error)} onRetry={() => courses.refetch()} />
      ) : courses.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No courses match your filters' : 'No courses yet'}
          action={!filtered && <Button onClick={() => setEditing('new')}>Create the first course</Button>}
        />
      ) : (
        <ul className="space-y-3">
          {courses.data.map((c) => (
            <li key={c.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <CourseImage src={c.imageUrl} category={c.category} className="h-16 w-24 shrink-0 rounded-lg" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold text-slate-900">{c.title}</h2>
                    <Tag tone="green">{c.category}</Tag>
                    {!c.active && <Tag>Closed</Tag>}
                  </div>
                  {c.description && <p className="mt-1 text-sm text-slate-600">{c.description}</p>}
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" small onClick={() => setEditing(c)}>
                    Edit
                  </Button>
                  <Button variant="secondary" small onClick={() => setConfirm({ kind: 'toggle', course: c })}>
                    {c.active ? 'Close' : 'Reopen'}
                  </Button>
                  <Button variant="danger" small onClick={() => setConfirm({ kind: 'delete', course: c })}>
                    Delete
                  </Button>
                </div>
              </div>
              <div className="mt-4 max-w-md">
                <SeatBar approved={c.approvedCount} pending={c.pendingCount} capacity={c.capacity} />
              </div>
              {!c.active && c.pendingCount > 0 && (
                <div className="mt-3 flex flex-wrap items-center gap-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  <span>
                    {c.pendingCount} pending request{c.pendingCount === 1 ? '' : 's'} can’t be approved while this
                    course is closed.
                  </span>
                  <Button variant="secondary" small onClick={() => setConfirm({ kind: 'reject-pending', course: c })}>
                    Reject them all
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {editing && <CourseFormModal course={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}

      {confirm && (
        <ConfirmDialog
          title={
            confirm.kind === 'delete'
              ? 'Delete this course?'
              : confirm.kind === 'reject-pending'
                ? 'Reject all pending requests?'
                : confirm.course.active
                  ? 'Close this course?'
                  : 'Reopen this course?'
          }
          message={
            confirm.kind === 'delete'
              ? `“${confirm.course.title}” will be removed. If it already has enrollments it is closed instead, so the history is kept.`
              : confirm.kind === 'reject-pending'
                ? `All ${confirm.course.pendingCount} pending request(s) for “${confirm.course.title}” will be rejected. This cannot be undone.`
                : confirm.course.active
                  ? `Employees will no longer be able to request “${confirm.course.title}”. Pending requests stay until you decide them.`
                  : `Employees will be able to request “${confirm.course.title}” again.`
          }
          confirmLabel={
            confirm.kind === 'delete' ? 'Delete' : confirm.kind === 'reject-pending' ? 'Reject all' : confirm.course.active ? 'Close course' : 'Reopen course'
          }
          danger={confirm.kind !== 'toggle' || confirm.course.active}
          loading={act.isPending}
          onConfirm={() => act.mutate(confirm)}
          onCancel={() => setConfirm(null)}
        />
      )}
    </>
  )
}
