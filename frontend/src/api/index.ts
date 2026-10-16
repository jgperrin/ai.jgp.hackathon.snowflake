import { httpApi } from './http'
import type { Api } from './types'

export * from './types'
export { ApiError } from './http'

/** True only under `npm run dev:mock`; the constant `false` in any build. */
export const MOCK = import.meta.env.DEV && import.meta.env.VITE_MOCK === '1'

/*
 * The mock is only reachable through this guarded dynamic import. In a build,
 * import.meta.env.DEV is replaced by `false`, the branch is dropped and the
 * mock module is never emitted.
 */
let mockApi: Promise<Api> | null = null
function impl(): Promise<Api> {
  if (import.meta.env.DEV && import.meta.env.VITE_MOCK === '1') {
    mockApi ??= import('./mock').then((m) => m.mockApi)
    return mockApi
  }
  return Promise.resolve(httpApi)
}

export const api: Api = {
  listDatabases: async () => (await impl()).listDatabases(),
  listSchemas: async (db) => (await impl()).listSchemas(db),
  listTables: async (db, schema) => (await impl()).listTables(db, schema),
  generate: async (req) => (await impl()).generate(req),
  toYaml: async (bundle) => (await impl()).toYaml(bundle),
  enrich: async (bundle) => (await impl()).enrich(bundle),
  apply: async (bundle, accepted) => (await impl()).apply(bundle, accepted),
  runQuality: async (bundle) => (await impl()).runQuality(bundle),
  save: async (bundle) => (await impl()).save(bundle),
  listProducts: async () => (await impl()).listProducts(),
  getProduct: async (id) => (await impl()).getProduct(id),
  exportOsi: async (bundle) => (await impl()).exportOsi(bundle),
}
