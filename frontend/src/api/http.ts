import { recordError } from '../lib/recentErrors'
import { ApiError } from './errors'
import type { Api, Bundle, EnrichResponse } from './types'

export { ApiError } from './errors'

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

async function send(method: Method, path: string, body?: unknown): Promise<Response> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      // The session lives in a cookie; same-origin is the default, stated so nobody "fixes" it to omit.
      credentials: 'same-origin',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    const message = `Backend unreachable (${method} ${path}): ${String(cause)}`
    recordError(0, `${method} ${path}`, message)
    throw new ApiError(0, message)
  }

  if (!response.ok) {
    const { message, code } = await errorBody(response, method, path)
    throw new ApiError(response.status, message, code)
  }
  return response
}

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  return (await (await send(method, path, body)).json()) as T
}

/** Same as request, but a given status (401 on /me, 404 on /demo) means "nothing" rather than an error. */
async function orNull<T>(status: number, call: Promise<T>): Promise<T | null> {
  try {
    return await call
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === status) {
      return null
    }
    throw cause
  }
}

/** Backend errors come as {message, code?} or as a Spring ProblemDetail {detail}; fall back to the status line. */
async function errorBody(
  response: Response,
  method: string,
  path: string,
): Promise<{ message: string; code: string | null }> {
  const body = await parseErrorBody(response, method, path)
  // Kept for the Help panel's report (status, call and message only).
  recordError(response.status, `${method} ${path}`, body.message)
  return body
}

async function parseErrorBody(
  response: Response,
  method: string,
  path: string,
): Promise<{ message: string; code: string | null }> {
  const fallback = `${method} ${path} failed: ${response.status} ${response.statusText}`
  let text = ''
  try {
    text = await response.text()
  } catch {
    return { message: fallback, code: null }
  }
  if (!text) {
    // A bare 502/503/504 comes from a proxy in front of the backend, not from the backend itself.
    if (response.status >= 502 && response.status <= 504) {
      return { message: 'The server is unreachable or restarting. Try again in a moment.', code: null }
    }
    return { message: fallback, code: null }
  }
  try {
    const json = JSON.parse(text) as { message?: string; detail?: string; title?: string; code?: string }
    return { message: json.message || json.detail || json.title || fallback, code: json.code ?? null }
  } catch {
    return { message: `${fallback} — ${text.slice(0, 300)}`, code: null }
  }
}

const enc = encodeURIComponent

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

type EnrichJob = {
  id: string
  status: 'RUNNING' | 'DONE' | 'FAILED'
  elapsedSeconds: number
  result: EnrichResponse | null
  error: string | null
}

const POLL_MS = 2000
const MAX_POLL_FAILURES = 5

/**
 * A Cortex analysis takes 30 to 90 s. Held as one request, any proxy with a shorter read timeout
 * cuts it with a bare 502, so the analysis runs as a server-side job and is polled instead. One
 * failed poll (a network blip, a proxy hiccup) is retried rather than ending the analysis.
 */
async function enrichViaJob(bundle: Bundle): Promise<EnrichResponse> {
  let job = await request<EnrichJob>('POST', `${API}/enrich/jobs`, bundleBody(bundle))
  let failures = 0
  while (job.status === 'RUNNING') {
    await sleep(POLL_MS)
    try {
      job = await request<EnrichJob>('GET', `${API}/enrich/jobs/${enc(job.id)}`)
      failures = 0
    } catch (cause) {
      const transient = cause instanceof ApiError && (cause.status === 0 || cause.status >= 502)
      if (!transient || ++failures >= MAX_POLL_FAILURES) {
        throw cause
      }
    }
  }
  if (job.status === 'FAILED' || !job.result) {
    throw new ApiError(500, job.error || 'The Cortex analysis failed.')
  }
  return job.result
}

/*
 * The agreed contract wraps a lone bundle as {bundle}; the backend as built
 * (yaml, enrich, quality/run) reads the Bundle itself at the top level. Send
 * both shapes at once: Jackson ignores the unknown key either way, so these
 * calls work whichever way each endpoint lands.
 */
const bundleBody = (bundle: Bundle) => ({ ...bundle, bundle })

/**
 * Built from the base URL so every path is correct both at the domain root and under the
 * production subpath. BASE_URL always ends in '/', so this is '/contextbuilder/api' in a
 * production build and '/api' in dev. Hardcoding '/api/...' would send the browser to the
 * domain root and 404 on every call.
 */
const API = `${import.meta.env.BASE_URL}api`

export const httpApi: Api = {
  demoCredentials: () => orNull(404, request('GET', `${API}/auth/demo`)),
  login: (credentials) => request('POST', `${API}/auth/login`, credentials),
  logout: async () => {
    await send('POST', `${API}/auth/logout`)
  },
  me: () => orNull(401, request('GET', `${API}/auth/me`)),
  getConnection: () => request('GET', `${API}/me/connection`),
  getConnectionDefaults: () => request('GET', `${API}/me/connection/defaults`),
  saveConnection: (update) => request('PUT', `${API}/me/connection`, update),
  listDatabases: () => request('GET', `${API}/catalog/databases`),
  listSchemas: (db) => request('GET', `${API}/catalog/databases/${enc(db)}/schemas`),
  listTables: (db, schema) =>
    request('GET', `${API}/catalog/databases/${enc(db)}/schemas/${enc(schema)}/tables`),
  generate: (req) => request('POST', `${API}/products/generate`, req),
  toYaml: (bundle) => request('POST', `${API}/products/yaml`, bundleBody(bundle)),
  enrich: (bundle) => enrichViaJob(bundle),
  apply: (bundle, accepted) => request('POST', `${API}/products/apply`, { bundle, accepted }),
  runQuality: (bundle) => request('POST', `${API}/quality/run`, bundleBody(bundle)),
  save: (bundle) => request('POST', `${API}/products/save`, bundleBody(bundle)),
  listProducts: () => request('GET', `${API}/products`),
  getProduct: (id) => request('GET', `${API}/products/${enc(id)}`),
  listSaves: () => request('GET', `${API}/products/saves`),
  getSaveArtifacts: (id) => request('GET', `${API}/products/${enc(id)}/artifacts`),
  deleteSave: (id) => request('DELETE', `${API}/products/${enc(id)}`),
  exportOsi: (bundle) => request('POST', `${API}/export/osi`, bundleBody(bundle)),
}
