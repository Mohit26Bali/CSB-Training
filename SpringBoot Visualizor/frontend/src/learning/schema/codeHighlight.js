/**
 * A deliberately tiny tokenizer for the Code Inspector: it only needs to make Java annotations, strings, comments and a
 * few keywords stand out. It never evaluates or rewrites the code, and joining the token texts always gives back the
 * original line exactly (verified by a test), so the inspector cannot display code that differs from the source.
 */
const KEYWORDS = new Set([
  'public', 'private', 'protected', 'class', 'interface', 'record', 'enum', 'return', 'new', 'final', 'static', 'void',
  'if', 'else', 'throw', 'throws', 'extends', 'implements', 'import', 'package', 'this', 'null', 'true', 'false',
])

const TOKEN = /(\/\/.*$|"""|"(?:[^"\\]|\\.)*"|@[A-Za-z_][\w.]*|\b[A-Za-z_]\w*\b|\s+|.)/g

export function tokenizeLine(line, language = 'java') {
  if (language !== 'java') return [{ type: 'plain', text: line }]
  const tokens = []
  for (const match of line.matchAll(TOKEN)) {
    const text = match[0]
    let type = 'plain'
    if (text.startsWith('//')) type = 'comment'
    else if (text.startsWith('"')) type = 'string'
    else if (text.startsWith('@')) type = 'annotation'
    else if (KEYWORDS.has(text)) type = 'keyword'
    tokens.push({ type, text })
  }
  return tokens
}
