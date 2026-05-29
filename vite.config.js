import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Environment variables with VITE_ prefix are automatically exposed to client-side code
  // No additional configuration needed - Vite handles this automatically
})
