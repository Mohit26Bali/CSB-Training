// Splits a .sql file into statements, remembering the comment lines written just above each one.
// It understands '...' "..." `...` strings (with '' and \' escapes), -- line comments and /* */ block comments,
// so a semicolon inside a string or a comment never ends a statement. DELIMITER blocks (stored routines) are not used
// by the assignments and are not supported.

/**
 * @param {string} source the whole file
 * @returns {{ text: string, comments: string[], line: number }[]}
 */
export function splitStatements(source) {
  const statements = []
  let current = ''
  let pendingComments = []
  let line = 1
  let startLine = 1
  let i = 0
  const n = source.length

  const flush = () => {
    const text = current.trim()
    if (text) statements.push({ text, comments: pendingComments, line: startLine })
    current = ''
    pendingComments = []
  }

  while (i < n) {
    const ch = source[i]
    const next = source[i + 1]

    if (ch === '\n') line++

    // line comment
    if ((ch === '-' && next === '-' && (source[i + 2] === ' ' || source[i + 2] === '\t' || source[i + 2] === '\n' || source[i + 2] === '\r' || i + 2 >= n)) || ch === '#') {
      let end = source.indexOf('\n', i)
      if (end === -1) end = n
      const text = source.slice(i, end).replace(/^(--|#)\s?/, '').trim()
      if (current.trim() === '') pendingComments.push(text)
      i = end
      continue
    }

    // block comment
    if (ch === '/' && next === '*') {
      let end = source.indexOf('*/', i + 2)
      end = end === -1 ? n : end + 2
      line += (source.slice(i, end).match(/\n/g) || []).length
      i = end
      continue
    }

    // quoted string or identifier
    if (ch === "'" || ch === '"' || ch === '`') {
      if (current.trim() === '') startLine = line
      let j = i + 1
      while (j < n) {
        if (source[j] === '\\' && ch !== '`') { j += 2; continue }
        if (source[j] === ch) {
          if (source[j + 1] === ch) { j += 2; continue } // doubled quote = escaped quote
          break
        }
        if (source[j] === '\n') line++
        j++
      }
      current += source.slice(i, j + 1)
      i = j + 1
      continue
    }

    if (ch === ';') {
      flush()
      i++
      continue
    }

    if (current.trim() === '' && !/\s/.test(ch)) startLine = line
    current += ch
    i++
  }
  flush()
  return statements
}

/** The first keyword of a statement in upper case (SELECT, INSERT, UPDATE, START, ...). */
export function firstKeyword(statement) {
  const m = /^\s*(\(|[A-Za-z]+)/.exec(statement)
  return m ? m[1].toUpperCase() : ''
}
