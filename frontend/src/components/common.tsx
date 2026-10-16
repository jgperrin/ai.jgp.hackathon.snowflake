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

/**
 * Context Builder's brand mark: a stellar-plate snow crystal. Six-fold symmetry, a hexagonal
 * plate with a spoke and a node at each vertex (tables joined into a product). Original
 * geometry, drawn for this app; public/favicon.svg repeats it, so change both together.
 */
export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 24 24" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round">
        <path d="M12 6l5.2 3v6L12 18l-5.2-3V9z" />
        <path d="M12 6V3.4M17.2 9l2.25-1.3M17.2 15l2.25 1.3M12 18v2.6M6.8 15l-2.25 1.3M6.8 9 4.55 7.7" />
      </g>
      <circle cx="12" cy="12" r="2" />
      <circle cx="12" cy="2.6" r="1.6" />
      <circle cx="20.14" cy="7.3" r="1.6" />
      <circle cx="20.14" cy="16.7" r="1.6" />
      <circle cx="12" cy="21.4" r="1.6" />
      <circle cx="3.86" cy="16.7" r="1.6" />
      <circle cx="3.86" cy="7.3" r="1.6" />
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
