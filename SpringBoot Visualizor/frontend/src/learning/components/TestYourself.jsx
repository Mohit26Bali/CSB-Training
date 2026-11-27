import { useState } from 'react'
import { ButtonLink } from '../../components/ui/Button'
import { arenaUrl } from '../schema/scenario'

/**
 * The "Test" stage of every module: a link into the Challenge Arena using its supported URL parameters
 * (topic, difficulty, count). The arena pre-selects them; the learner still presses Start.
 */
export default function TestYourself({ topicSlug, topics }) {
  const [difficulty, setDifficulty] = useState('')
  const [count, setCount] = useState(5)
  const topic = topics?.find((t) => t.slug === topicSlug)
  const available = topic?.activeQuestionCount ?? 0

  return (
    <div className="rounded-lg border border-line bg-surface p-4 shadow-sm">
      <p className="text-sm text-fg-2">
        Check what you learned with questions on this topic in the Challenge Arena.
        {topics && ` ${available} active question(s) available.`}
      </p>
      <div className="mt-3 flex flex-wrap items-end gap-3 text-sm">
        <label>
          Difficulty{' '}
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)} className="rounded-md border border-line-strong px-2 py-1.5">
            <option value="">Any</option>
            <option>EASY</option>
            <option>MEDIUM</option>
            <option>HARD</option>
          </select>
        </label>
        <label>
          Questions{' '}
          <input
            type="number"
            min={1}
            max={20}
            value={count}
            onChange={(e) => setCount(Math.min(20, Math.max(1, parseInt(e.target.value, 10) || 1)))}
            className="w-20 rounded-md border border-line-strong px-2 py-1.5"
          />
        </label>
        {available > 0 ? (
          <ButtonLink to={arenaUrl({ topic: topicSlug, difficulty, count })}>
            Test yourself in the Arena
          </ButtonLink>
        ) : (
          <span className="text-muted">No questions available for this topic yet.</span>
        )}
      </div>
    </div>
  )
}
