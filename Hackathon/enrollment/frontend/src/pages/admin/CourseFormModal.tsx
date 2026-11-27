import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Course, CourseInput } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, CourseImage, Field, Modal } from '../../components/ui'

interface Props {
  course: Course | null // null = creating
  onClose: () => void
}

export function CourseFormModal({ course, onClose }: Props) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    title: course?.title ?? '',
    description: course?.description ?? '',
    category: course?.category ?? '',
    imageUrl: course?.imageUrl ?? '',
    capacity: String(course?.capacity ?? 10),
    active: course?.active ?? true,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: (input: CourseInput) => (course ? adminApi.updateCourse(course.id, input) : adminApi.createCourse(input)),
    onSuccess: (saved) => {
      toast.success(course ? `Saved “${saved.title}”` : `Created “${saved.title}”`)
      queryClient.invalidateQueries()
      onClose()
    },
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setFieldErrors({})
        setFormError(errorMessage(err))
      }
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    save.mutate({
      title: form.title,
      description: form.description,
      category: form.category,
      imageUrl: form.imageUrl,
      capacity: Number(form.capacity),
      active: form.active,
    })
  }

  const text = (key: 'title' | 'description' | 'category' | 'capacity' | 'imageUrl') => ({
    value: form[key],
    onChange: (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value })),
    error: fieldErrors[key],
  })

  return (
    <Modal title={course ? 'Edit course' : 'New course'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <Field label="Title" maxLength={100} {...text('title')} />
        <Field label="Description" maxLength={255} {...text('description')} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Category" maxLength={50} {...text('category')} />
          <Field
            label="Capacity"
            type="number"
            min={course ? Math.max(1, course.approvedCount) : 1}
            max={10000}
            hint={course && course.approvedCount > 0 ? `${course.approvedCount} already approved` : undefined}
            {...text('capacity')}
          />
        </div>
        <div className="flex items-start gap-3">
          <div className="flex-1">
            <Field
              label="Image (optional)"
              placeholder="/course-images/java-basics.jpg or https://…"
              hint="A site path or an http(s) link to a picture"
              {...text('imageUrl')}
            />
          </div>
          <CourseImage src={form.imageUrl.trim() || null} category={form.category || '?'} className="mt-6 h-14 w-20 shrink-0 rounded-lg" />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
            className="size-4 rounded border-slate-300"
          />
          Open for enrollment
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {course ? 'Save changes' : 'Create course'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
