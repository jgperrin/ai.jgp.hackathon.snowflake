import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Credentials, SessionUser } from '../api'
import { messageOf } from '../lib/util'
import { BrandMark, Spinner } from './common'

interface Props {
  onSignedIn: (user: SessionUser) => void
}

/** Username/password sign-in. The demo account, when the backend has one, is written next to the fields for the judges. */
export function SignIn({ onSignedIn }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [demo, setDemo] = useState<Credentials | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    // No demo account (404) or any failure: simply show no hint.
    api.demoCredentials().then(
      (credentials) => live && setDemo(credentials),
      () => live && setDemo(null),
    )
    return () => {
      live = false
    }
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      onSignedIn(await api.login({ username, password }))
    } catch (cause) {
      setError(messageOf(cause))
      setBusy(false)
    }
  }

  return (
    <main className="signin">
      <form className="card signin-card" onSubmit={(event) => void submit(event)}>
        <div className="brand">
          <BrandMark />
          <span className="brand-name">Context Builder</span>
          <span className="brand-badge">v{__APP_VERSION__}</span>
        </div>
        <h1 className="signin-title">Sign in</h1>
        <p className="muted signin-sub">From Snowflake schema to AI-ready data product.</p>

        {demo && (
          <p className="banner banner-info signin-demo">
            <span className="banner-text">
              Judges: sign in with <strong className="mono">{demo.username}</strong> /{' '}
              <strong className="mono">{demo.password}</strong>
            </span>
          </p>
        )}

        <label className="form-field">
          <span className="field-label">Username</span>
          <input
            type="text"
            name="username"
            autoComplete="username"
            autoFocus
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </label>
        <label className="form-field">
          <span className="field-label">Password</span>
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>

        {error && (
          <p className="field-error" role="alert">
            <span aria-hidden="true">⚠ </span>
            {error}
          </p>
        )}

        <div className="actions">
          <button type="submit" className="btn-primary btn-large" disabled={busy || !username || !password}>
            {busy ? <Spinner label="Signing in…" /> : 'Sign in'}
          </button>
        </div>
      </form>
    </main>
  )
}
