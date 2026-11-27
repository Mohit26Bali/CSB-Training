/** The gateway is the only backend address the browser uses. Change it in .env (see .env.example), not here. */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
