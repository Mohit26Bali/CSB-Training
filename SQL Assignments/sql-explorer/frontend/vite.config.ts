/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// API_TARGET lets the end-to-end tests point the same proxy at their own backend.
const api = process.env.API_TARGET ?? 'http://127.0.0.1:4100'

export default defineConfig({
  plugins: [react()],
  server: { port: 5300, proxy: { '/api': api } },
  preview: { port: 5300, proxy: { '/api': api } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], globals: true, include: ['src/**/*.test.{ts,tsx}'] },
})
