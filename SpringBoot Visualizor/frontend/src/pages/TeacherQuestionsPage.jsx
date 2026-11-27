import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listTopics } from '../api/arenaApi'
import { describeError } from '../api/errors'
import { listQuestions, previewQuestion, setQuestionActive } from '../api/teacherApi'
import AppHeader from '../components/AppHeader'
import DifficultyBadge from '../components/DifficultyBadge'
import StudentPreview from '../components/StudentPreview'

const PAGE_SIZE = 10

export default function TeacherQuestionsPage() {
  const [filters, setFilters] = useState({ topicId: '', difficulty: '', active: '', mine: false, q: '' })
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [topics, setTopics] = useState([])
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [preview, setPreview] = useState(null)

  useEffect(() => {
    listTopics()
      .then(setTopics)
      .catch((err) => setError(describeError(err).message))
  }, [])

  useEffect(() => {
    let cancelled = false
    listQuestions({ ...filters, page, size: PAGE_SIZE })
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
  }, [filters, page])

  function changeFilter(patch) {
    setLoading(true)
    setPage(0)
    setFilters((prev) => ({ ...prev, ...patch }))
  }

  async function toggleActive(question) {
    const verb = question.active ? 'Deactivate' : 'Activate'
    if (!window.confirm(`${verb} this question? ${question.active ? 'Students will no longer receive it.' : ''}`)) return
    setBusyId(question.id)
    setError(null)
    try {
      const updated = await setQuestionActive(question.id, !question.active)
      setData((prev) => ({
        ...prev,
        content: prev.content.map((q) => (q.id === question.id ? { ...q, active: updated.active } : q)),
      }))
    } catch (err) {
      setError(describeError(err).message) // e.g. 403 when the question belongs to someone else
    } finally {
      setBusyId(null)
    }
  }

  async function showPreview(question) {
    setError(null)
    try {
      setPreview(await previewQuestion(question.id))
    } catch (err) {
      setError(describeError(err).message)
    }
  }

  return (
    <div className="min-h-screen bg-app text-fg">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-4 p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="text-2xl font-bold">Question bank</h1>
          <Link to="/teacher/questions/new" className="rounded bg-accent px-3 py-1.5 text-sm text-on-accent hover:bg-accent-hover">
            New question
          </Link>
        </div>

        <div className="flex flex-wrap items-end gap-3 text-sm">
          <form
            onSubmit={(e) => {
              e.preventDefault()
              changeFilter({ q: search.trim() })
            }}
            className="flex gap-2"
          >
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search question text"
              aria-label="Search question text"
              className="rounded border border-line-strong px-3 py-1.5"
            />
            <button className="rounded bg-code-header px-3 py-1.5 text-fg">Search</button>
          </form>
          <select
            aria-label="Topic"
            value={filters.topicId}
            onChange={(e) => changeFilter({ topicId: e.target.value })}
            className="rounded-md border border-line-strong px-2 py-1.5.5"
          >
            <option value="">All topics</option>
            {topics.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <select
            aria-label="Difficulty"
            value={filters.difficulty}
            onChange={(e) => changeFilter({ difficulty: e.target.value })}
            className="rounded-md border border-line-strong px-2 py-1.5.5"
          >
            <option value="">Any difficulty</option>
            <option>EASY</option>
            <option>MEDIUM</option>
            <option>HARD</option>
          </select>
          <select
            aria-label="Status"
            value={filters.active}
            onChange={(e) => changeFilter({ active: e.target.value })}
            className="rounded-md border border-line-strong px-2 py-1.5.5"
          >
            <option value="">Active and inactive</option>
            <option value="true">Active only</option>
            <option value="false">Inactive only</option>
          </select>
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={filters.mine} onChange={(e) => changeFilter({ mine: e.target.checked })} />
            Mine only
          </label>
        </div>

        {error && (
          <p role="alert" className="rounded-md border border-error-line bg-error-soft px-3 py-2 text-sm text-error">
            {error}
          </p>
        )}

        <div className="overflow-x-auto rounded-lg border border-line bg-surface shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="bg-subtle text-fg-2">
              <tr>
                <th className="px-3 py-2">Question</th>
                <th className="px-3 py-2">Topic</th>
                <th className="px-3 py-2">Level</th>
                <th className="px-3 py-2">Author</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={6} className="px-3 py-4 text-muted">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && data?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-3 py-4 text-muted">
                    No questions match.
                  </td>
                </tr>
              )}
              {!loading &&
                data?.content.map((q) => (
                  <tr key={q.id} className="border-t border-line-subtle align-top">
                    <td className="max-w-md px-3 py-2">{q.text.length > 110 ? `${q.text.slice(0, 110)}…` : q.text}</td>
                    <td className="px-3 py-2">{q.topicName}</td>
                    <td className="px-3 py-2">
                      <DifficultyBadge difficulty={q.difficulty} />
                    </td>
                    <td className="px-3 py-2">{q.createdBy}</td>
                    <td className="px-3 py-2">{q.active ? 'Active' : 'Inactive'}</td>
                    <td className="space-x-2 px-3 py-2 whitespace-nowrap">
                      <button onClick={() => showPreview(q)} className="text-accent hover:underline">
                        Preview
                      </button>
                      {q.editable ? (
                        <>
                          <Link to={`/teacher/questions/${q.id}/edit`} className="text-accent hover:underline">
                            Edit
                          </Link>
                          <button
                            onClick={() => toggleActive(q)}
                            disabled={busyId === q.id}
                            className="text-accent hover:underline disabled:opacity-50"
                          >
                            {q.active ? 'Deactivate' : 'Activate'}
                          </button>
                        </>
                      ) : (
                        <>
                          <Link to={`/teacher/questions/${q.id}/edit`} className="text-muted hover:underline">
                            View
                          </Link>
                          <span className="text-xs text-muted">(read-only)</span>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {data && (
          <div className="flex items-center justify-between text-sm text-fg-2">
            <span>
              {data.totalElements} question(s) - page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  setLoading(true)
                  setPage(page - 1)
                }}
                disabled={page === 0}
                className="rounded border border-line-strong px-3 py-1 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => {
                  setLoading(true)
                  setPage(page + 1)
                }}
                disabled={page + 1 >= data.totalPages}
                className="rounded border border-line-strong px-3 py-1 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        )}

        {preview && (
          <div role="dialog" aria-label="Question preview" className="fixed inset-0 flex items-center justify-center bg-overlay p-4">
            <div className="max-h-full w-full max-w-lg overflow-y-auto rounded-lg border border-line bg-surface p-4 shadow-sm-lg">
              <h2 className="mb-2 text-lg font-semibold">Student view (from the server)</h2>
              <StudentPreview text={preview.text} difficulty={preview.difficulty} options={preview.options} />
              <button onClick={() => setPreview(null)} className="mt-4 rounded border border-line-strong px-3 py-1">
                Close
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
