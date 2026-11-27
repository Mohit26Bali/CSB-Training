import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 5173 is the address the gateway allows for CORS (CORS_ALLOWED_ORIGIN). Keep them the same.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
