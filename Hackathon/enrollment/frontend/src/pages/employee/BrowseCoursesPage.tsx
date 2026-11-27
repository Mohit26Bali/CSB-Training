import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { courseApi, enrollmentApi } from '../../api/endpoints'
import type { Course, Enrollment } from '../../api/types'
import { Button, CourseImage, EmptyState, ErrorState, LoadingState, PageHeader, SeatBar, StatusBadge, Tag } from '../../components/ui'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../hooks'

export function BrowseCoursesPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const debouncedSearch = useDebounced(search)

  const courses = useQuery({
    queryKey: ['courses', debouncedSearch, category],
    queryFn: () => courseApi.browse({ search: debouncedSearch, category }),
    placeholderData: (previous) => previous,
  })
  const categories = useQuery({ queryKey: ['categories'], queryFn: courseApi.categories })
  const mine = useQuery({ queryKey: ['my-enrollments'], queryFn: enrollmentApi.mine })

  const request = useMutation({
    mutationFn: (courseId: number) => enrollmentApi.request(courseId),
    onSuccess: (enrollment) => {
      toast.success(`Request sent for “${enrollment.courseTitle}”. An admin will review it.`)
    },
    onError: (err) => toast.error(errorMessage(err)),
    // Refetch on success and on failure: a failure usually means the page was out of date (course just filled up).
    onSettled: () => queryClient.invalidateQueries(),
  })

  const byCourse = new Map<number, Enrollment>((mine.data ?? []).map((e) => [e.courseId, e]))
  const filtered = search !== '' || category !== ''

  return (
    <>
      <PageHeader title="Browse courses" subtitle="Request a seat in an internal training course." />

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
        {filtered && (
          <Button
            variant="ghost"
            onClick={() => {
              setSearch('')
              setCategory('')
            }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {courses.isPending ? (
        <LoadingState label="Loading courses…" />
      ) : courses.isError ? (
        <ErrorState message={errorMessage(courses.error)} onRetry={() => courses.refetch()} />
      ) : courses.data.length === 0 ? (
        <EmptyState
          title={filtered ? 'No courses match your filters' : 'No courses are open right now'}
          hint={filtered ? 'Try a different search or category.' : 'Check back soon.'}
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {courses.data.map((course) => (
            <CourseCard
              key={course.id}
              course={course}
              enrollment={byCourse.get(course.id)}
              pendingThis={request.isPending && request.variables === course.id}
              onRequest={() => request.mutate(course.id)}
            />
          ))}
        </ul>
      )}
    </>
  )
}

function CourseCard({
  course,
  enrollment,
  pendingThis,
  onRequest,
}: {
  course: Course
  enrollment: Enrollment | undefined
  pendingThis: boolean
  onRequest: () => void
}) {
  const full = course.approvedCount >= course.capacity

  return (
    <li className="group flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
      <div className="relative h-40 overflow-hidden">
        <CourseImage
          src={course.imageUrl}
          category={course.category}
          className="size-full transition-transform duration-500 group-hover:scale-105"
        />
        {full && (
          <span className="absolute left-3 top-3 rounded-full bg-rose-600 px-2.5 py-0.5 text-xs font-semibold text-white">
            Full
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col p-5">
      <div className="flex items-start justify-between gap-2">
        <h2 className="font-semibold text-slate-900">{course.title}</h2>
        <Tag tone="green">{course.category}</Tag>
      </div>
      <p className="mt-2 flex-1 text-sm text-slate-600">{course.description || 'No description provided.'}</p>
      <div className="mt-4">
        <SeatBar approved={course.approvedCount} capacity={course.capacity} />
      </div>
      <div className="mt-4 flex items-center justify-between gap-2">
        {enrollment ? (
          <>
            <StatusBadge status={enrollment.status} />
            <Link to="/my-enrollments" className="text-sm font-medium text-brand-700 hover:underline">
              View
            </Link>
          </>
        ) : (
          <Button className="w-full" loading={pendingThis} disabled={full} onClick={onRequest}>
            {full ? 'Course full' : 'Request enrollment'}
          </Button>
        )}
      </div>
      </div>
    </li>
  )
}
