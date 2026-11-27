import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client'
import { applicationApi } from '../../api/endpoints'
import type { Program } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, Field, formatMoney, Modal, SelectField, TextAreaField } from '../../components/ui'
import { previewMeetsCriteria, previewScore } from '../../eligibility'
import { ScoreBadge } from '../../components/ScoreBadge'

export function ApplyModal({ program, onClose }: { program: Program; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ gpa: '', annualFamilyIncome: '', course: '', yearOfStudy: '', statement: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const apply = useMutation({
    mutationFn: () =>
      applicationApi.apply({
        programId: program.id,
        gpa: Number(form.gpa),
        annualFamilyIncome: Number(form.annualFamilyIncome),
        course: form.course,
        yearOfStudy: Number(form.yearOfStudy),
        statement: form.statement,
      }),
    onSuccess: () => {
      toast.success(`Application sent for ${program.name}`)
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
      queryClient.invalidateQueries() // e.g. the programme closed or you already applied: refresh what is shown
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const gpa = Number(form.gpa)
  const income = Number(form.annualFamilyIncome)
  const ready = form.gpa !== '' && form.annualFamilyIncome !== '' && gpa >= 0 && gpa <= 10 && income >= 0

  function submit(e: FormEvent) {
    e.preventDefault()
    setFieldErrors({})
    setFormError(null)
    apply.mutate()
  }

  return (
    <Modal title={`Apply: ${program.name}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-600">
          Award {formatMoney(program.amount)}. Criteria: CGPA of at least {program.minGpa.toFixed(2)} and family income up to {formatMoney(program.maxFamilyIncome)} a year.
        </p>
        {formError && (
          <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {formError}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <Field label="CGPA (out of 10)" type="number" inputMode="decimal" min="0" max="10" step="0.01" value={form.gpa} onChange={set('gpa')} error={fieldErrors.gpa} />
          <Field
            label="Annual family income (₹)"
            type="number"
            inputMode="decimal"
            min="0"
            step="1000"
            value={form.annualFamilyIncome}
            onChange={set('annualFamilyIncome')}
            error={fieldErrors.annualFamilyIncome}
          />
        </div>
        <div className="grid grid-cols-[1fr_8rem] gap-4">
          <Field label="Course of study" maxLength={100} placeholder="B.Tech Computer Science" value={form.course} onChange={set('course')} error={fieldErrors.course} />
          <SelectField label="Year" value={form.yearOfStudy} onChange={set('yearOfStudy')} error={fieldErrors.yearOfStudy}>
            <option value="">Select</option>
            {[1, 2, 3, 4, 5, 6].map((y) => (
              <option key={y} value={y}>
                Year {y}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="min-h-6" aria-live="polite">
          {ready ? (
            <ScoreBadge score={previewScore(gpa, income, program)} meets={previewMeetsCriteria(gpa, income, program)} />
          ) : (
            <span className="text-xs text-slate-500">Enter your CGPA and family income to preview your eligibility.</span>
          )}
        </div>
        <TextAreaField
          label="Why are you applying?"
          maxLength={1000}
          rows={4}
          placeholder="Tell the committee about your goals and why this support matters to you."
          value={form.statement}
          onChange={set('statement')}
          error={fieldErrors.statement}
          hint={`${form.statement.length}/1000`}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={apply.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={apply.isPending}>
            Submit application
          </Button>
        </div>
      </form>
    </Modal>
  )
}
