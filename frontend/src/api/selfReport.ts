import { recentErrors } from '../lib/recentErrors'
import type { RecentError } from '../lib/recentErrors'

/*
 * The Help panel's report (DIND#60), ported from Control Center. Multipart, so it does not go
 * through the JSON send() helper in http.ts. The bounds match SelfReportService on the backend,
 * which checks them again.
 */
export const MAX_DESCRIPTION = 2000
export const MAX_FILES = 5
export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const MAX_TOTAL_BYTES = 25 * 1024 * 1024
export const ACCEPT = 'image/*,application/pdf,text/plain'

export type ReportContext = {
  appVersion: string
  step: string | null
  route: string
  user: string
  userAgent: string
  viewport: { width: number; height: number }
  /** A boolean only: no connection setting ever leaves the browser. */
  snowflakeConnected: boolean | null
  mockBackend: boolean
  timezone: string
  recentErrors: RecentError[]
}

export type ReportError = { title: string; message: string }
export type ReportResult = { ok: true; issueUrl: string; reportId: string } | { ok: false; error: ReportError }

export function isAllowedType(type: string): boolean {
  return type.startsWith('image/') || type === 'application/pdf' || type === 'text/plain'
}

/** Adds files one by one; the first one over a bound stops it with a message. */
export function addFiles(current: File[], incoming: File[]): { files: File[]; error: ReportError | null } {
  const next = [...current]
  for (const f of incoming) {
    if (!isAllowedType(f.type)) {
      return {
        files: next,
        error: { title: 'File type not allowed', message: `${f.name} is not an image, a PDF or plain text.` },
      }
    }
    if (next.length >= MAX_FILES) {
      return { files: next, error: { title: 'Too many files', message: `At most ${MAX_FILES} files per report.` } }
    }
    if (f.size > MAX_FILE_BYTES) {
      return { files: next, error: { title: 'File too large', message: `${f.name} is over 10 MB.` } }
    }
    if (next.reduce((sum, x) => sum + x.size, 0) + f.size > MAX_TOTAL_BYTES) {
      return { files: next, error: { title: 'Attachments too large', message: 'Attachments must total 25 MB or less.' } }
    }
    next.push(f)
  }
  return { files: next, error: null }
}

export function buildContext(user: string, snowflakeConnected: boolean | null, mockBackend: boolean): ReportContext {
  // The wizard keeps its step in component state; the stepper marks the current one in the DOM.
  const step = document.querySelector('.stepper-item.is-current .stepper-title')?.textContent ?? null
  return {
    appVersion: __APP_VERSION__,
    step,
    route: `${window.location.pathname}${window.location.search}${window.location.hash}`,
    user,
    userAgent: navigator.userAgent,
    viewport: { width: window.innerWidth, height: window.innerHeight },
    snowflakeConnected,
    mockBackend,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    recentErrors: recentErrors(),
  }
}

const PATH = `${import.meta.env.BASE_URL}api/self-report`

/** Maps the backend's stable {message, reason} to copy; never throws. */
export async function submitReport(description: string, files: File[], context: ReportContext): Promise<ReportResult> {
  // Under dev:mock nothing leaves the browser: the Vite proxy would otherwise hand this to
  // whatever backend is on :8080 (DIND#74). Same guard as api/index.ts, so builds drop it.
  if (import.meta.env.DEV && import.meta.env.VITE_MOCK === '1') {
    const reportId = Math.random().toString(16).slice(2, 10)
    return { ok: true, issueUrl: `https://mock.invalid/self-report/${reportId}`, reportId }
  }
  const form = new FormData()
  form.append('description', description)
  form.append('context', JSON.stringify(context))
  files.forEach((f) => form.append('attachment', f, f.name))
  let response: Response
  try {
    // No Content-Type: the browser sets the multipart boundary itself.
    response = await fetch(PATH, { method: 'POST', body: form, credentials: 'same-origin' })
  } catch (cause) {
    return { ok: false, error: { title: 'Could not send the report', message: `The server is unreachable: ${String(cause)}` } }
  }
  let body: { issueUrl?: string; reportId?: string; message?: string; reason?: string } = {}
  try {
    body = await response.json()
  } catch {
    // A proxy error page, not JSON: fall through to the status.
  }
  if (response.ok && body.issueUrl) {
    return { ok: true, issueUrl: body.issueUrl, reportId: body.reportId ?? '' }
  }
  return { ok: false, error: describe(response.status, body.reason ?? '', body.message ?? '') }
}

function describe(status: number, reason: string, message: string): ReportError {
  switch (reason) {
    case 'not_configured':
      return {
        title: 'Reporting is not set up here',
        message: 'Problem reporting is not configured on this server. Please file the issue on GitHub instead.',
      }
    case 'rate_limited':
      return { title: 'Too many reports', message: message || 'Too many reports in the last hour. Try again later.' }
    case 'github_unreachable':
    case 'github_rejected':
    case 'github_auth':
      return { title: 'GitHub did not take the report', message: message || 'GitHub failed, not Context Builder.' }
    case 'invalid_request':
      return { title: 'Report refused', message }
  }
  if (status === 401) {
    return { title: 'Signed out', message: 'Your session ended. Sign in again, then resend the report.' }
  }
  if (status === 413) {
    return { title: 'Attachments too large', message: 'The upload was refused for its size. Remove or shrink a file.' }
  }
  return { title: 'Could not send the report', message: message || `HTTP ${status}` }
}
