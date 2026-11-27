export default function SectionHeading({ title, description, action, level = 2, className = '' }) {
  const Tag = `h${level}`
  return (
    <div className={`flex flex-wrap items-end justify-between gap-2 ${className}`}>
      <div>
        <Tag className="text-lg font-semibold text-fg">{title}</Tag>
        {description && <p className="mt-0.5 text-sm text-fg-2">{description}</p>}
      </div>
      {action}
    </div>
  )
}
