import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server forwards /api to Spring Boot, so the browser sees one origin and CORS never applies.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5101,
    proxy: {
      '/api': 'http://localhost:8101',
    },
  },
})
