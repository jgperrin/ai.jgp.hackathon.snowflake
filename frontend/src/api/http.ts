import { ApiError } from './errors'
import type { Api, Bundle } from './types'

export { ApiError } from './errors'

type Method = 'GET' | 'POST' | 'PUT'

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
    throw new ApiError(0, `Backend unreachable (${method} ${path}): ${String(cause)}`)
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
  const fallback = `${method} ${path} failed: ${response.status} ${response.statusText}`
  let text = ''
  try {
    text = await response.text()
  } catch {
    return { message: fallback, code: null }
  }
  if (!text) {
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
  saveConnection: (update) => request('PUT', `${API}/me/connection`, update),
  listDatabases: () => request('GET', `${API}/catalog/databases`),
  listSchemas: (db) => request('GET', `${API}/catalog/databases/${enc(db)}/schemas`),
  listTables: (db, schema) =>
    request('GET', `${API}/catalog/databases/${enc(db)}/schemas/${enc(schema)}/tables`),
  generate: (req) => request('POST', `${API}/products/generate`, req),
  toYaml: (bundle) => request('POST', `${API}/products/yaml`, bundleBody(bundle)),
  enrich: (bundle) => request('POST', `${API}/enrich`, bundleBody(bundle)),
  apply: (bundle, accepted) => request('POST', `${API}/products/apply`, { bundle, accepted }),
  runQuality: (bundle) => request('POST', `${API}/quality/run`, bundleBody(bundle)),
  save: (bundle) => request('POST', `${API}/products/save`, bundleBody(bundle)),
  listProducts: () => request('GET', `${API}/products`),
  getProduct: (id) => request('GET', `${API}/products/${enc(id)}`),
  exportOsi: (bundle) => request('POST', `${API}/export/osi`, bundleBody(bundle)),
}
