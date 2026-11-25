import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server forwards /api to the Spring Boot backend (backend/, port 8095), so the browser only ever talks to one
// origin and CORS never comes into play. src/api/client.ts uses the relative base URL '/api'.
// API_TARGET lets the end-to-end tests (playwright.config.ts) point the same proxy at their own backend.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5180,
    proxy: {
      '/api': process.env.API_TARGET ?? 'http://localhost:8095',
    },
  },
})
