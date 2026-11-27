export default function FormField({ label, error, ...inputProps }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-fg-2">{label}</span>
      <input
        className={`mt-1 block w-full rounded border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent ${
          error ? 'border-error' : 'border-line-strong'
        }`}
        aria-invalid={error ? 'true' : 'false'}
        {...inputProps}
      />
      {error && <span className="mt-1 block text-xs text-error">{error}</span>}
    </label>
  )
}
