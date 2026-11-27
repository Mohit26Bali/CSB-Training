/** The class a learner must declare is the one the starter code declares; it differs for every problem. */
export function starterClassName(starterCode) {
  const m = /^\s*(?:public\s+)?(?:final\s+)?(?:abstract\s+)?class\s+([A-Za-z_]\w*)/m.exec(starterCode ?? '')
  return m ? m[1] : 'the class from the starter code'
}
