import { useEffect, useMemo, useRef, useState } from 'react'
import type { ClipboardEvent, FormEvent } from 'react'
import { MOCK } from '../api'
import { ACCEPT, MAX_DESCRIPTION, MAX_FILES, MAX_TOTAL_BYTES, addFiles, buildContext, submitReport } from '../api/selfReport'
import type { ReportError } from '../api/selfReport'
import { Spinner } from './common'

interface Props {
  user: string
  connected: boolean | null
  onClose: () => void
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

/**
 * Report a problem (DIND#60), ported from Control Center's HelpPanel. A native modal dialog,
 * like the connection panel: showModal() traps focus and Escape fires 'cancel'; a click on the
 * backdrop lands on the dialog element itself and closes it too.
 */
export function HelpPanel({ user, connected, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const [description, setDescription] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<ReportError | null>(null)
  const [issueUrl, setIssueUrl] = useState('')
  // Built once on open, so what the disclosure shows is exactly what is sent.
  const context = useMemo(() => buildContext(user, connected, MOCK), [user, connected])
  const totalBytes = files.reduce((sum, f) => sum + f.size, 0)

  useEffect(() => {
    const element = dialog.current
    if (element && !element.open) {
      element.showModal()
    }
    return () => element?.close()
  }, [])

  function add(incoming: File[]) {
    const result = addFiles(files, incoming)
    setFiles(result.files)
    setError(result.error)
  }

  function onPaste(event: ClipboardEvent) {
    const pasted = Array.from(event.clipboardData.items)
      .filter((item) => item.kind === 'file')
      .map((item) => item.getAsFile())
      .filter((f): f is File => f !== null)
    if (pasted.length) {
      event.preventDefault()
      add(pasted)
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!description.trim()) return
    setSending(true)
    setError(null)
    const result = await submitReport(description, files, context)
    setSending(false)
    if (result.ok) {
      setIssueUrl(result.issueUrl)
    } else {
      setError(result.error)
    }
  }

  const issueNumber = issueUrl.match(/\/issues\/(\d+)\/?$/)?.[1]

  return (
    <dialog
      ref={dialog}
      className="panel help-panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="help-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        if (event.target === dialog.current) onClose()
      }}
    >
      <form className="panel-body" onSubmit={(event) => void submit(event)} onPaste={onPaste}>
        <div className="panel-head">
          <h2 id="help-title">Report a problem</h2>
          <span className="spacer" />
          <button type="button" className="btn-ghost btn-small" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        {issueUrl ? (
          <div className="banner banner-ok" role="status">
            <span className="banner-text">
              Thanks, your report is filed as{' '}
              <a href={issueUrl} target="_blank" rel="noreferrer">
                {issueNumber ? `issue #${issueNumber}` : issueUrl}
              </a>
              .
            </span>
          </div>
        ) : (
          <>
            {error && (
              <div className="banner banner-error" role="alert">
                <span className="banner-text">
                  <strong>{error.title}.</strong> {error.message}
                </span>
              </div>
            )}

            <label className="form-field">
              <span className="field-label">
                What happened?
                <span className="hint">paste a screenshot with Cmd/Ctrl+V</span>
              </span>
              <textarea
                rows={6}
                required
                maxLength={MAX_DESCRIPTION}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="What you were doing, what you expected, what you got."
              />
              <span className="hint help-count">
                {description.length} / {MAX_DESCRIPTION}
              </span>
            </label>

            <div className="form-field">
              <span className="field-label">
                Attachments
                <span className="hint">
                  images, PDF, plain text · up to {MAX_FILES} files, 10 MB each, {formatBytes(MAX_TOTAL_BYTES)} total
                </span>
              </span>
              <input
                ref={fileInput}
                type="file"
                multiple
                accept={ACCEPT}
                hidden
                onChange={(event) => {
                  add(Array.from(event.target.files ?? []))
                  event.target.value = ''
                }}
              />
              {files.length > 0 && (
                <ul className="help-files">
                  {files.map((f, i) => (
                    <li key={`${f.name}-${i}`}>
                      <span className="help-file-name">{f.name}</span>
                      <span className="muted">{formatBytes(f.size)}</span>
                      <button
                        type="button"
                        className="btn-ghost btn-small"
                        aria-label={`Remove ${f.name}`}
                        onClick={() => setFiles(files.filter((_, j) => j !== i))}
                      >
                        ✕
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div>
                <button
                  type="button"
                  className="btn-secondary btn-small"
                  disabled={files.length >= MAX_FILES}
                  onClick={() => fileInput.current?.click()}
                >
                  Add file
                </button>
                {files.length > 0 && (
                  <span className="muted help-total">
                    {files.length} / {MAX_FILES} · {formatBytes(totalBytes)}
                  </span>
                )}
              </div>
            </div>

            <details className="help-context">
              <summary>What is sent with the report</summary>
              <p className="muted">
                Version, step, page, your user name, browser and window size, whether Snowflake is connected (yes or
                no only) and the last API errors. Never your Snowflake password, connection settings or session.
              </p>
              <pre>{JSON.stringify(context, null, 2)}</pre>
            </details>
          </>
        )}

        <div className="actions">
          <span className="spacer" />
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
          {!issueUrl && (
            <button type="submit" className="btn-primary" disabled={sending || !description.trim()}>
              {sending ? <Spinner label="Sending…" /> : 'Send report'}
            </button>
          )}
        </div>
      </form>
    </dialog>
  )
}
