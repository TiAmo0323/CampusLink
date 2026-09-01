import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/uploads': 'http://localhost:8080' } },
  preview: { port: 4173, proxy: { '/api': 'http://localhost:8080', '/uploads': 'http://localhost:8080' } },
  build: { outDir: 'dist', rollupOptions:{output:{manualChunks:{vue:['vue','vue-router','pinia'],element:['element-plus','@element-plus/icons-vue'],http:['axios']}}} }
})
