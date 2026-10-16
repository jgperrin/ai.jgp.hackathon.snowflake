import { ApiError, NO_CONNECTION } from './errors'
import { httpApi } from './http'
import { recordError } from '../lib/recentErrors'
import type { Api } from './types'

export * from './types'
export { ApiError, NO_CONNECTION } from './errors'

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

/*
 * Global session handling. Any call (other than the sign-in calls themselves) that
 * answers 401 means the session is gone; a 409 NO_CONNECTION means the user is
 * signed in but has no working Snowflake connection. The shell (App) listens and
 * reacts once, so no step component has to know about either case. The error is
 * still thrown, so the caller's own spinner and error state unwind normally.
 */
export type SessionEvent = { kind: 'unauthorized' } | { kind: 'no-connection'; message: string }
type Listener = (event: SessionEvent) => void
const listeners = new Set<Listener>()

export function onSessionEvent(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

async function guarded<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call()
  } catch (cause) {
    if (cause instanceof ApiError) {
      if (cause.status === 401) {
        listeners.forEach((l) => l({ kind: 'unauthorized' }))
      } else if (cause.status === 409 && cause.code === NO_CONNECTION) {
        listeners.forEach((l) => l({ kind: 'no-connection', message: cause.message }))
      }
    } else {
      // ApiErrors are recorded where they are raised (http.ts); anything else is a bug worth reporting.
      recordError(-1, 'client', String(cause))
    }
    throw cause
  }
}

export const api: Api = {
  // Sign-in calls: a 401 here is an answer, not a lost session.
  demoCredentials: async () => (await impl()).demoCredentials(),
  login: async (credentials) => (await impl()).login(credentials),
  logout: async () => (await impl()).logout(),
  me: async () => (await impl()).me(),
  getConnection: () => guarded(async () => (await impl()).getConnection()),
  getConnectionDefaults: () => guarded(async () => (await impl()).getConnectionDefaults()),
  saveConnection: (update) => guarded(async () => (await impl()).saveConnection(update)),
  listDatabases: () => guarded(async () => (await impl()).listDatabases()),
  listSchemas: (db) => guarded(async () => (await impl()).listSchemas(db)),
  listTables: (db, schema) => guarded(async () => (await impl()).listTables(db, schema)),
  generate: (req) => guarded(async () => (await impl()).generate(req)),
  toYaml: (bundle) => guarded(async () => (await impl()).toYaml(bundle)),
  enrich: (bundle) => guarded(async () => (await impl()).enrich(bundle)),
  apply: (bundle, accepted) => guarded(async () => (await impl()).apply(bundle, accepted)),
  runQuality: (bundle) => guarded(async () => (await impl()).runQuality(bundle)),
  save: (bundle) => guarded(async () => (await impl()).save(bundle)),
  listProducts: () => guarded(async () => (await impl()).listProducts()),
  getProduct: (id) => guarded(async () => (await impl()).getProduct(id)),
  exportOsi: (bundle) => guarded(async () => (await impl()).exportOsi(bundle)),
}
