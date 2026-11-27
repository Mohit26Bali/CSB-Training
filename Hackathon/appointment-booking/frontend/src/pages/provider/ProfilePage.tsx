import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { providerApi } from '../../api/endpoints'
import type { Provider, ProfileInput } from '../../api/types'
import { ProviderPhoto } from '../../components/ProviderPhoto'
import { useToast } from '../../components/Toast'
import { Button, ErrorState, Field, LoadingState, PageHeader, TextAreaField } from '../../components/ui'

export function ProfilePage() {
  const profile = useQuery({ queryKey: ['provider', 'profile'], queryFn: providerApi.profile })
  if (profile.isPending) return <LoadingState />
  if (profile.isError) return <ErrorState message={errorMessage(profile.error)} onRetry={() => profile.refetch()} />
  return <ProfileForm provider={profile.data} />
}

function ProfileForm({ provider }: { provider: Provider }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ProfileInput>({ title: provider.title, description: provider.description ?? '', location: provider.location ?? '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => providerApi.updateProfile(form),
    onSuccess: () => {
      toast.success('Profile saved')
      queryClient.invalidateQueries()
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
    save.mutate()
  }

  return (
    <>
      <PageHeader title="Your profile" subtitle="This is what customers see when they browse providers." />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <form onSubmit={submit} noValidate className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          {formError && (
            <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {formError}
            </p>
          )}
          <Field label="Speciality" maxLength={60} value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} error={fieldErrors.title} hint="For example Dentist or Physiotherapist" />
          <TextAreaField label="About you" rows={3} maxLength={400} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} error={fieldErrors.description} />
          <Field label="Where" maxLength={100} value={form.location} onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))} error={fieldErrors.location} />
          <div className="flex justify-end">
            <Button type="submit" loading={save.isPending}>
              Save profile
            </Button>
          </div>
        </form>
        <aside className="h-fit rounded-xl border border-slate-200 bg-white p-5 text-center shadow-sm">
          <ProviderPhoto id={provider.id} name={provider.name} title={form.title || provider.title} className="mx-auto size-24 text-3xl" />
          <h2 className="mt-3 font-semibold text-slate-900">{provider.name}</h2>
          <p className="font-medium text-brand-700">{form.title}</p>
          {form.location && <p className="text-sm text-slate-500">{form.location}</p>}
          {form.description && <p className="mt-2 text-sm text-slate-600">{form.description}</p>}
        </aside>
      </div>
    </>
  )
}
