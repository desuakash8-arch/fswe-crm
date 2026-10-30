import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

if (process.env.VERCEL) {
  const apiUrl = process.env.VITE_API_BASE_URL?.trim()
  let valid = false
  try {
    const url = new URL(apiUrl || '')
    valid = url.protocol === 'https:' && !url.username && !url.password &&
      !url.search && !url.hash && url.pathname.replace(/\/+$/, '') === '/api'
  } catch { /* The build error below explains the missing setting. */ }
  if (!valid) throw new Error('Set VITE_API_BASE_URL in Vercel to https://YOUR-BACKEND.onrender.com/api, then redeploy.')
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8085', changeOrigin: true } },
  },
  preview: {
    proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8085', changeOrigin: true } },
  },
})
