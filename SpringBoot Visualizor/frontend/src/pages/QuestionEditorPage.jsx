import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { listTopics } from '../api/arenaApi'
import { describeError } from '../api/errors'
import { createQuestion, getQuestion, updateQuestion } from '../api/teacherApi'
import AppHeader from '../components/AppHeader'
import StudentPreview from '../components/StudentPreview'

const MIN_OPTIONS = 2
const MAX_OPTIONS = 6

const emptyForm = () => ({
  text: '',
  explanation: '',
  topicId: '',
  difficulty: 'EASY',
  options: [
    { text: '', correct: true },
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
  ],
})

function toForm(detail) {
  return {
    text: detail.text,
    explanation: detail.explanation,
    topicId: String(detail.topicId),
    difficulty: detail.difficulty,
    options: detail.options.map((o) => ({ id: o.id, text: o.text, correct: o.correct })),
  }
}

export default function QuestionEditorPage() {
  const { id } = useParams()
  const isNew = id === undefined
  const navigate = useNavigate()
  const [topics, setTopics] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [meta, setMeta] = useState({ version: null, editable: true, active: true, createdBy: null })
  const [loading, setLoading] = useState(!isNew)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [showTeacherView, setShowTeacherView] = useState(false)

  useEffect(() => {
    listTopics()
      .then(setTopics)
      .catch((err) => setError(describeError(err).message))
  }, [])

  useEffect(() => {
    if (isNew) return
    let cancelled = false
    getQuestion(id)
      .then((detail) => {
        if (cancelled) return
        setForm(toForm(detail))
        setMeta({ version: detail.version, editable: detail.editable, active: detail.active, createdBy: detail.createdBy })
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
  }, [id, isNew])

  const readOnly = !isNew && !meta.editable

  const setField = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))

  function setOption(index, patch) {
    setForm((prev) => ({
      ...prev,
      options: prev.options.map((o, i) => (i === index ? { ...o, ...patch } : o)),
    }))
  }

  function markCorrect(index) {
    setForm((prev) => ({ ...prev, options: prev.options.map((o, i) => ({ ...o, correct: i === index })) }))
  }

  function addOption() {
    setForm((prev) => ({ ...prev, options: [...prev.options, { text: '', correct: false }] }))
  }

  function removeOption(index) {
    setForm((prev) => {
      const options = prev.options.filter((_, i) => i !== index)
      if (!options.some((o) => o.correct)) options[0] = { ...options[0], correct: true }
      return { ...prev, options }
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    setNotice(null)
    const body = {
      text: form.text,
      explanation: form.explanation,
      topicId: form.topicId ? Number(form.topicId) : null,
      difficulty: form.difficulty,
      options: form.options.map((o) => ({ id: o.id ?? null, text: o.text, correct: o.correct })),
      version: meta.version,
    }
    try {
      if (isNew) {
        const created = await createQuestion(body)
        navigate(`/teacher/questions/${created.id}/edit`, { replace: true })
      } else {
        const updated = await updateQuestion(id, body)
        setForm(toForm(updated))
        setMeta((prev) => ({ ...prev, version: updated.version }))
        setNotice('Saved.')
      }
    } catch (err) {
      const { message, fields } = describeError(err)
      setError(
        err.response?.status === 409 && !isNew ? `${message}. Reload the page to get the latest version.` : message,
      )
      setFieldErrors(fields)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-app">
        <AppHeader />
        <p className="p-6 text-muted">Loading…</p>
      </div>
    )
  }

  const inputClass = 'mt-1 block w-full rounded border border-line-strong px-3 py-2 text-sm disabled:bg-subtle'

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto grid max-w-5xl gap-6 p-4 sm:p-6 lg:grid-cols-2">
        <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-line bg-surface p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <h1 className="text-xl font-bold">{isNew ? 'New question' : readOnly ? 'View question' : 'Edit question'}</h1>
            <Link to="/teacher/questions" className="text-sm text-accent hover:underline">
              Back to list
            </Link>
          </div>
          {readOnly && (
            <p className="rounded bg-warning-soft p-2 text-sm text-warning">
              Read-only: this question was authored by {meta.createdBy}. Only its author or an administrator can change it.
            </p>
          )}
          {error && (
            <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
              {error}
            </p>
          )}
          {notice && <p className="rounded bg-success-soft p-2 text-sm text-success">{notice}</p>}

          <label className="block text-sm font-medium">
            Question
            <textarea
              value={form.text}
              onChange={setField('text')}
              rows={3}
              maxLength={2000}
              required
              disabled={readOnly}
              className={inputClass}
            />
            {fieldErrors.text && <span className="text-xs text-error">{fieldErrors.text}</span>}
          </label>

          <div className="grid grid-cols-2 gap-3">
            <label className="block text-sm font-medium">
              Topic
              <select value={form.topicId} onChange={setField('topicId')} required disabled={readOnly} className={inputClass}>
                <option value="">Select…</option>
                {topics.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm font-medium">
              Difficulty
              <select value={form.difficulty} onChange={setField('difficulty')} disabled={readOnly} className={inputClass}>
                <option>EASY</option>
                <option>MEDIUM</option>
                <option>HARD</option>
              </select>
            </label>
          </div>

          <fieldset>
            <legend className="text-sm font-medium">Options (select the single correct one)</legend>
            {fieldErrors.exactlyOneCorrectOption && (
              <p className="text-xs text-error">Exactly one option must be correct.</p>
            )}
            {fieldErrors.options && <p className="text-xs text-error">{fieldErrors.options}</p>}
            <div className="mt-2 space-y-2">
              {form.options.map((o, index) => (
                <div key={o.id ?? `new-${index}`} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="correct"
                    checked={o.correct}
                    onChange={() => markCorrect(index)}
                    disabled={readOnly}
                    aria-label={`Option ${index + 1} is correct`}
                  />
                  <input
                    value={o.text}
                    onChange={(e) => setOption(index, { text: e.target.value })}
                    placeholder={`Option ${index + 1}`}
                    maxLength={500}
                    required
                    disabled={readOnly}
                    aria-label={`Option ${index + 1} text`}
                    className="w-full rounded border border-line-strong px-3 py-1.5 text-sm disabled:bg-subtle"
                  />
                  {!readOnly && form.options.length > MIN_OPTIONS && (
                    <button
                      type="button"
                      onClick={() => removeOption(index)}
                      className="text-sm text-error hover:underline"
                      aria-label={`Remove option ${index + 1}`}
                    >
                      Remove
                    </button>
                  )}
                </div>
              ))}
            </div>
            {!readOnly && form.options.length < MAX_OPTIONS && (
              <button type="button" onClick={addOption} className="mt-2 text-sm text-accent hover:underline">
                + Add option
              </button>
            )}
          </fieldset>

          <label className="block text-sm font-medium">
            Explanation (shown to students after they submit)
            <textarea
              value={form.explanation}
              onChange={setField('explanation')}
              rows={4}
              maxLength={4000}
              required
              disabled={readOnly}
              className={inputClass}
            />
            {fieldErrors.explanation && <span className="text-xs text-error">{fieldErrors.explanation}</span>}
          </label>

          {!readOnly && (
            <button
              type="submit"
              disabled={saving}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-on-accent shadow-sm transition-colors hover:bg-accent-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving ? 'Saving…' : isNew ? 'Create question' : 'Save changes'}
            </button>
          )}
        </form>

        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Preview</h2>
          <p className="text-sm text-fg-2">This is how a student will see the question (live, from the form).</p>
          <StudentPreview text={form.text} difficulty={form.difficulty} options={form.options} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={showTeacherView} onChange={(e) => setShowTeacherView(e.target.checked)} />
            Show correct answer and explanation (teacher view)
          </label>
          {showTeacherView && (
            <div className="rounded-lg border border-line bg-surface p-3 shadow-sm text-sm">
              <ul className="space-y-1">
                {form.options.map((o, i) => (
                  <li key={o.id ?? i} className={o.correct ? 'font-medium text-success' : ''}>
                    {o.correct ? '✓ ' : '○ '}
                    {o.text || `(option ${i + 1})`}
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-fg-2">
                <span className="font-medium">Explanation: </span>
                {form.explanation || '(none yet)'}
              </p>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
