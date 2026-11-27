import DifficultyBadge from './DifficultyBadge'

/** Renders a question exactly as a student sees it: text and option texts, no correctness, no explanation. */
export default function StudentPreview({ text, difficulty, options }) {
  return (
    <div className="rounded border border-line p-3">
      <p className="font-medium">
        {text || <span className="text-muted">(question text)</span>} <DifficultyBadge difficulty={difficulty} />
      </p>
      <div className="mt-2 space-y-1">
        {options.map((o, i) => (
          <label key={o.id ?? i} className="flex items-start gap-2 text-sm">
            <input type="radio" disabled className="mt-1" />
            <span>{o.text || <span className="text-muted">(option {i + 1})</span>}</span>
          </label>
        ))}
      </div>
    </div>
  )
}
