// Checks the notes for the mistakes that creep into long markdown files:
//   - table-of-contents links that point to a heading that does not exist
//   - links to other files in this folder that do not exist
//   - code fences that are opened but never closed
//   - leftover TODO / FIXME markers and tabs in headings
//   - anything that looks like a real secret (long keys, tokens, passwords)
// Run:  node check-notes.js        (Node 18+, nothing to install). Exit code 1 if anything is found.
const fs = require('node:fs');
const path = require('node:path');

const dir = __dirname;
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.md')).sort();
const problems = [];
const report = (file, line, message) => problems.push(`${file}:${line}  ${message}`);

/** GitHub's heading-to-anchor rule: lower-case, drop punctuation, spaces become dashes. */
function slug(heading) {
  return heading
    .toLowerCase()
    .replace(/`/g, '')
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

// A code fence, also when it starts after a list marker ("6. ```java").
const FENCE = /^\s*(?:\d+\.\s+|[-*]\s+)?```/;

const SECRET_PATTERNS = [
  [/eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}/, 'a real-looking JWT'],
  [/\b(?:sk|pk)[-_](?:live|test)?[-_]?[A-Za-z0-9]{20,}\b/, 'an API key'],
  [/\bAKIA[0-9A-Z]{16}\b/, 'an AWS access key'],
  [/\bgh[pousr]_[A-Za-z0-9]{30,}\b/, 'a GitHub token'],
  [/-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/, 'a private key'],
  [/\b[A-Za-z0-9+/]{43}=/, 'a long base64 value (check it is a made-up example)'],
];

for (const file of files) {
  const lines = fs.readFileSync(path.join(dir, file), 'utf8').split(/\r?\n/);
  const anchors = new Set();
  const seen = new Map();
  let inFence = false;
  let fenceOpenedAt = 0;

  lines.forEach((text, i) => {
    if (FENCE.test(text)) {
      inFence = !inFence;
      if (inFence) fenceOpenedAt = i + 1;
      return;
    }
    if (inFence) return;
    const heading = text.match(/^(#{1,6})\s+(.*\S)\s*$/);
    if (heading) {
      let a = slug(heading[2]);
      const n = seen.get(a) || 0;
      seen.set(a, n + 1);
      if (n > 0) a = `${a}-${n}`;
      anchors.add(a);
    }
  });
  if (inFence) report(file, fenceOpenedAt, 'code fence is opened and never closed');

  inFence = false;
  lines.forEach((text, i) => {
    const lineNo = i + 1;
    if (FENCE.test(text)) {
      inFence = !inFence;
      return;
    }
    for (const [pattern, what] of SECRET_PATTERNS) if (pattern.test(text)) report(file, lineNo, `looks like ${what}`);
    if (inFence) return;
    if (/\b(TODO|FIXME|XXX)\b/.test(text)) report(file, lineNo, 'leftover TODO / FIXME marker');
    if (/^#+\s.*\t/.test(text)) report(file, lineNo, 'tab character in a heading');

    // [text](#anchor) and [text](other-file.md#anchor)
    for (const m of text.matchAll(/\]\(([^)\s]+)\)/g)) {
      const target = m[1];
      if (/^(https?:|mailto:)/.test(target)) continue;
      const [filePart, hash] = target.split('#');
      if (filePart === '') {
        if (hash && !anchors.has(hash)) report(file, lineNo, `link to a heading that does not exist: #${hash}`);
      } else if (!fs.existsSync(path.join(dir, decodeURIComponent(filePart)))) {
        report(file, lineNo, `link to a file that does not exist: ${filePart}`);
      }
    }
  });
}

if (problems.length === 0) {
  console.log(`OK: ${files.length} notes checked, no problems found.`);
} else {
  console.log(problems.join('\n'));
  console.log(`\n${problems.length} problem(s) in ${files.length} notes.`);
  process.exitCode = 1;
}
