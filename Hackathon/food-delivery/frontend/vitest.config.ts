import { defineConfig } from 'vitest/config'

// Unit tests cover plain logic (cart, pricing, keys), so they need no React plugin and no DOM.
export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
})
