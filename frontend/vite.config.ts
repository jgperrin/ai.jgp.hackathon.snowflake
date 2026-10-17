import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * The tool is served under a path prefix, not at the domain root, so every asset
 * URL and API path has to carry it. It is applied in dev too: a subpath mistake
 * should show up on localhost, not for the first time in production.
 *
 * Which prefix is a build input, because there is more than one instance now
 * (DIND#106): the original at /contextbuilder/ and the next one at
 * /contextbuilder-vnext/. `VITE_BASE` selects it; the default keeps every
 * existing build and the dev server exactly as they were. It is normalised to
 * lead and end with a slash, since every path here is joined with it.
 */
const BASE = normaliseBase(process.env.VITE_BASE ?? '/contextbuilder/')

function normaliseBase(value: string): string {
  const leading = value.startsWith('/') ? value : `/${value}`
  return leading.endsWith('/') ? leading : `${leading}/`
}

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
