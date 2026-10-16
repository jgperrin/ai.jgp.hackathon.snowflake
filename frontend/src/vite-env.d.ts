/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "1" switches src/api to the in-memory mock (dev only, see `npm run dev:mock`). */
  readonly VITE_MOCK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
