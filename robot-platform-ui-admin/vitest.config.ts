import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

/** Keep unit tests independent of the full application Vite configuration. */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts']
  }
})
