import { useState } from 'react'

export function ErrorBanner({ error, onDismiss }: { error: string; onDismiss?: () => void }) {
  if (!error) {
    return null
  }
  return (
    <div className="banner banner-error" role="alert">
      <svg className="banner-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path
          fillRule="evenodd"
          d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
          clipRule="evenodd"
        />
      </svg>
      <span className="banner-text">
        <strong className="banner-title">Error</strong>
        {error}
      </span>
      {onDismiss && (
        <button type="button" className="btn-ghost" onClick={onDismiss}>
          Dismiss
        </button>
      )}
    </div>
  )
}

/** The ops/ux "hourglass" ring (components.md): two arcs in currentColor, spinning. */
function Ring({ className }: { className: string }) {
  return (
    <svg className={`spinner ${className}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
      <circle className="spinner-track" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="spinner-head" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

/** Inline ring with an optional present-participle label, for buttons and table cells. */
export function Spinner({ label }: { label?: string }) {
  return (
    <span className="spinner-wrap">
      <Ring className="spinner-sm" />
      {label && <span>{label}</span>}
    </span>
  )
}

/** The ring standing in for a region that is still loading (ops/ux LoadingState). */
export function LoadingState({ caption }: { caption?: string }) {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <Ring className="spinner-md" />
      {caption && <span className="loading-caption">{caption}</span>}
    </div>
  )
}

/** Context Builder's brand mark: stacked layers (schema → contract → product). */
export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2 2 7l10 5 10-5-10-5z" />
      <path d="m2 12 10 5 10-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="m2 17 10 5 10-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
    </svg>
  )
}

export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard can be blocked (http, permissions); the Download button still works.
    }
  }
  return (
    <button type="button" className="btn-secondary" onClick={copy}>
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}
