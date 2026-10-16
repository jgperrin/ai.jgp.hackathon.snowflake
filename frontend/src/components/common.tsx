import { useState } from 'react'

export function ErrorBanner({ error, onDismiss }: { error: string; onDismiss?: () => void }) {
  if (!error) {
    return null
  }
  return (
    <div className="banner banner-error" role="alert">
      <strong>Error</strong>
      <span className="banner-text">{error}</span>
      {onDismiss && (
        <button type="button" className="btn-ghost btn-small" onClick={onDismiss} aria-label="Dismiss error">
          ✕
        </button>
      )}
    </div>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="spinner-wrap">
      <span className="spinner" aria-hidden="true" />
      {label && <span>{label}</span>}
    </span>
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
