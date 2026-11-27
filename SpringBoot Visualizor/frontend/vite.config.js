import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{js,jsx}'],
  },
  server: {
    port: 5173,
    // Same-origin in development: the browser talks to :5173 and Vite forwards /api and /actuator
    // to Spring Boot, so no CORS configuration is needed during development.
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
