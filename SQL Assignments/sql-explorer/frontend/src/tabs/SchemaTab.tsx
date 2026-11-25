import type { AssignmentDetail, TableInfo } from '../api'

function keyBadges(table: TableInfo, column: string, key: string) {
  const badges: string[] = []
  if (key === 'PRI') badges.push('PK')
  if (table.foreignKeys.some((f) => f.column === column)) badges.push('FK')
  if (key === 'UNI') badges.push('UNIQUE')
  return badges
}

export function SchemaTab({ a }: { a: AssignmentDetail }) {
  const relations = [...new Set(a.tables.flatMap((t) => t.foreignKeys.map((f) => `${t.name}.${f.column} → ${f.refTable}.${f.refColumn}`)))]
  return (
    <>
      {relations.length > 0 && (
        <>
          <h2>Relationships</h2>
          <ul className="relations">
            {relations.map((r) => (
              <li key={r}>
                <code>{r}</code>
              </li>
            ))}
          </ul>
        </>
      )}
      <div className="schema-grid">
        {a.tables.map((t) => (
          <section key={t.name} className="schema-card" aria-labelledby={`t-${t.name}`}>
            <h3 id={`t-${t.name}`}>
              {t.name} <span className="meta">{t.rowCount} rows</span>
            </h3>
            <table className="schema">
              <thead>
                <tr>
                  <th scope="col">Column</th>
                  <th scope="col">Type</th>
                  <th scope="col">Keys</th>
                  <th scope="col">Null?</th>
                </tr>
              </thead>
              <tbody>
                {t.columns.map((c) => {
                  const fk = t.foreignKeys.find((f) => f.column === c.name)
                  return (
                    <tr key={c.name}>
                      <td>
                        <code>{c.name}</code>
                      </td>
                      <td>
                        {c.type}
                        {c.extra ? <span className="meta"> {c.extra}</span> : null}
                      </td>
                      <td>
                        {keyBadges(t, c.name, c.key).map((b) => (
                          <span key={b} className={`pill key-${b.toLowerCase()}`}>
                            {b}
                          </span>
                        ))}
                        {fk && (
                          <span className="meta">
                            {' '}
                            → {fk.refTable}.{fk.refColumn}
                          </span>
                        )}
                      </td>
                      <td>{c.nullable ? 'yes' : 'NOT NULL'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {t.checks.length > 0 && (
              <ul className="checks">
                {t.checks.map((c) => (
                  <li key={c.name}>
                    CHECK <code>{c.clause}</code>
                  </li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
    </>
  )
}
