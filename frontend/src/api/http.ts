import type { Api, Bundle } from './types'

/** Raised for any non-2xx answer; the message is what the backend said, when it said something. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    throw new ApiError(0, `Backend unreachable (${method} ${path}): ${String(cause)}`)
  }

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response, method, path))
  }
  return (await response.json()) as T
}

/** Backend errors come as {message} or as a Spring ProblemDetail {detail}; fall back to the status line. */
async function errorMessage(response: Response, method: string, path: string): Promise<string> {
  const fallback = `${method} ${path} failed: ${response.status} ${response.statusText}`
  let text = ''
  try {
    text = await response.text()
  } catch {
    return fallback
  }
  if (!text) {
    return fallback
  }
  try {
    const json = JSON.parse(text) as { message?: string; detail?: string; title?: string }
    return json.message || json.detail || json.title || fallback
  } catch {
    return `${fallback} — ${text.slice(0, 300)}`
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
