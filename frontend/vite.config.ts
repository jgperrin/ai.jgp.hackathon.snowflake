import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * The tool is served at tools.actianlabs.com/contextbuilder, not at the domain root,
 * so every asset URL and API path has to carry this prefix. It is applied in dev too:
 * a subpath mistake should show up on localhost, not for the first time in production.
 */
const BASE = '/contextbuilder/'

/** The app version shown in the header pill and the footer (ops/ux footer-spec), read from package.json. */
const APP_VERSION = (JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string }).version

// https://vite.dev/config/
export default defineConfig({
  base: BASE,
  define: {
    __APP_VERSION__: JSON.stringify(APP_VERSION),
  },
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Strips the prefix, because the backend listens on /api/... unprefixed.
      [`${BASE}api`]: {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(BASE.slice(0, -1), ''),
      },
    },
  },
})
