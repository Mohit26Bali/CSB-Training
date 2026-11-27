/** Splits the small Markdown subset used by problem statements into blocks (see SimpleMarkdown). Pure text in, plain objects out. */
export function parseBlocks(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (line.trim() === '') {
      i += 1
    } else if (line.startsWith('```')) {
      const code = []
      i += 1
      while (i < lines.length && !lines[i].startsWith('```')) code.push(lines[i++])
      i += 1
      blocks.push({ type: 'code', text: code.join('\n') })
    } else if (/^###\s+/.test(line)) {
      blocks.push({ type: 'h4', text: line.replace(/^###\s+/, '') })
      i += 1
    } else if (/^##\s+/.test(line)) {
      blocks.push({ type: 'h3', text: line.replace(/^##\s+/, '') })
      i += 1
    } else if (/^[*-]\s+/.test(line)) {
      const items = []
      while (i < lines.length && /^[*-]\s+/.test(lines[i])) items.push(lines[i++].replace(/^[*-]\s+/, ''))
      blocks.push({ type: 'ul', items })
    } else if (/^\d+\.\s+/.test(line)) {
      const items = []
      while (i < lines.length && /^\d+\.\s+/.test(lines[i])) items.push(lines[i++].replace(/^\d+\.\s+/, ''))
      blocks.push({ type: 'ol', items })
    } else {
      const para = []
      while (i < lines.length && lines[i].trim() !== '' && !/^(```|#{2,3}\s|[*-]\s|\d+\.\s)/.test(lines[i])) para.push(lines[i++])
      blocks.push({ type: 'p', text: para.join(' ') })
    }
  }
  return blocks
}
