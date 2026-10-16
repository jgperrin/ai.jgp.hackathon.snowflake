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

export const httpApi: Api = {
  listDatabases: () => request('GET', '/api/catalog/databases'),
  listSchemas: (db) => request('GET', `/api/catalog/databases/${enc(db)}/schemas`),
  listTables: (db, schema) =>
    request('GET', `/api/catalog/databases/${enc(db)}/schemas/${enc(schema)}/tables`),
  generate: (req) => request('POST', '/api/products/generate', req),
  toYaml: (bundle) => request('POST', '/api/products/yaml', bundleBody(bundle)),
  enrich: (bundle) => request('POST', '/api/enrich', bundleBody(bundle)),
  apply: (bundle, accepted) => request('POST', '/api/products/apply', { bundle, accepted }),
  runQuality: (bundle) => request('POST', '/api/quality/run', bundleBody(bundle)),
  save: (bundle) => request('POST', '/api/products/save', bundleBody(bundle)),
  listProducts: () => request('GET', '/api/products'),
  getProduct: (id) => request('GET', `/api/products/${enc(id)}`),
  exportOsi: (bundle) => request('POST', '/api/export/osi', bundleBody(bundle)),
}
