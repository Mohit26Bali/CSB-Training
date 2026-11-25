import { Fragment } from 'react'

const KEYWORDS = new Set(
  `SELECT FROM WHERE GROUP BY HAVING ORDER LIMIT OFFSET JOIN INNER LEFT RIGHT FULL OUTER CROSS ON AS AND OR NOT IN IS NULL LIKE BETWEEN EXISTS
   UNION ALL DISTINCT CASE WHEN THEN ELSE END INSERT INTO VALUES UPDATE SET DELETE CREATE TABLE ALTER ADD DROP COLUMN PRIMARY KEY FOREIGN REFERENCES
   CHECK UNIQUE DEFAULT AUTO_INCREMENT CONSTRAINT WITH OVER PARTITION ASC DESC USE DATABASE START TRANSACTION COMMIT ROLLBACK SAVEPOINT MODIFY AFTER`
    .split(/\s+/),
)
const FUNCTIONS = new Set('COUNT SUM AVG MIN MAX ROUND COALESCE IFNULL DATE_FORMAT CONCAT NOW CURDATE DATEDIFF YEAR MONTH UPPER LOWER LENGTH RANK ROW_NUMBER DENSE_RANK'.split(' '))
const TOKEN = /(--[^\n]*|'(?:[^'\\]|\\.|'')*'|`[^`]*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z_0-9]*\b)/g

/** Breaks SQL into spans for colouring. No HTML is ever built from the text, so nothing in a query can inject markup. */
export function SqlBlock({ sql, className = '' }: { sql: string; className?: string }) {
  const parts = sql.split(TOKEN)
  return (
    <pre className={`sql ${className}`} tabIndex={0}>
      <code>
        {parts.map((part, i) => {
          if (!part) return null
          let kind = ''
          if (part.startsWith('--')) kind = 'c'
          else if (part.startsWith("'")) kind = 's'
          else if (/^\d/.test(part)) kind = 'n'
          else if (KEYWORDS.has(part.toUpperCase())) kind = 'k'
          else if (FUNCTIONS.has(part.toUpperCase())) kind = 'f'
          return kind ? <span key={i} className={`tok-${kind}`}>{part}</span> : <Fragment key={i}>{part}</Fragment>
        })}
      </code>
    </pre>
  )
}
