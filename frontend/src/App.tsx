import { useEffect, useState } from 'react'
import { ApiError, MOCK, api, onSessionEvent } from './api'
import type { SessionUser } from './api'
import { ConnectionPanel } from './components/ConnectionPanel'
import { Footer } from './components/Footer'
import { SignIn } from './components/SignIn'
import { BrandMark, LoadingState } from './components/common'
import { Wizard } from './Wizard'

/** undefined while the session is being checked, null when signed out. */
type Session = SessionUser | null | undefined

/** null while unknown (just signed in, being checked). */
type ConnectionState = boolean | null

const NOT_CONNECTED_YET = 'No Snowflake connection is saved for your profile yet.'

/**
 * The shell: sign-in gate, header with the signed-in user and the Snowflake
 * connection panel, then the wizard. Global API events (401, 409 NO_CONNECTION)
 * land here so no step has to handle them.
 */
export default function App() {
  const [user, setUser] = useState<Session>(undefined)
  const [connected, setConnected] = useState<ConnectionState>(null)
  // null: panel closed; a string: open, with the reason it opened on its own ('' when the user opened it).
  const [panel, setPanel] = useState<string | null>(null)
  // Drives the footer heart: false only when the session check could not reach the backend at all.
  const [backendUp, setBackendUp] = useState(true)

  useEffect(() => {
    let live = true
    api.me().then(
      (me) => live && setUser(me),
      (cause: unknown) => {
        if (!live) return
        setBackendUp(!(cause instanceof ApiError && cause.status === 0))
        setUser(null)
      },
    )
    return () => {
      live = false
    }
  }, [])

  useEffect(
    () =>
      onSessionEvent((event) => {
        if (event.kind === 'unauthorized') {
          setUser(null)
          setConnected(null)
          setPanel(null)
        } else {
          setPanel(event.message || NOT_CONNECTED_YET)
        }
      }),
    [],
  )

  // Once signed in, find out whether there is a connection; without one, open the panel straight away.
  const username = user?.username
  useEffect(() => {
    if (!username) {
      return
    }
    let live = true
    api.getConnection().then(
      (connection) => {
        if (!live) return
        setConnected(connection.connected)
        if (!connection.connected) {
          setPanel(connection.error || NOT_CONNECTED_YET)
        }
      },
      // A 401 is already handled globally; anything else, let the user fix it in the panel.
      () => live && setConnected(false),
    )
    return () => {
      live = false
    }
  }, [username])

  async function signOut() {
    try {
      await api.logout()
    } catch {
      // Signing out locally is what matters; the session expires server-side anyway.
    }
    setPanel(null)
    setConnected(null)
    setUser(null)
  }

  const mockStrip = MOCK && <div className="mock-strip">Mock backend · illustrative sample data, not Snowflake</div>
  const footer = <Footer healthy={backendUp} />

  if (user === undefined) {
    return (
      <div className="app">
        {mockStrip}
        <main className="signin">
          <LoadingState caption="Loading…" />
        </main>
        {footer}
      </div>
    )
  }

  if (user === null) {
    return (
      <div className="app">
        {mockStrip}
        <SignIn onSignedIn={setUser} />
        {footer}
      </div>
    )
  }

  return (
    <div className="app">
      {mockStrip}
      <header className="topnav">
        <div className="topnav-inner">
          {/* The brand is the "back to start" link (ops/ux parity: hover colours the name). */}
          <a className="topnav-brand" href={import.meta.env.BASE_URL} title="Back to start">
            <BrandMark />
            <span className="brand-name">Context Builder</span>
            <span className="brand-badge">v{__APP_VERSION__}</span>
          </a>
          <span className="spacer" />
          <div className="account">
            <span className="account-user">
              Signed in as <strong>{user.username}</strong>
            </span>
            <button type="button" className="btn-secondary" onClick={() => setPanel('')}>
              <span className={connected ? 'badge badge-ok' : 'badge badge-gray'}>
                {connected ? '● connected' : '○ not connected'}
              </span>
              Snowflake connection
            </button>
            <button type="button" className="btn-ghost" onClick={() => void signOut()}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="content">
        <div className="page-header">
          <h1>From Snowflake schema to AI-ready data product</h1>
          <p className="standards" aria-label="Standards">
            <span className="badge badge-indigo">ODCS v3.2</span>
            <span className="badge badge-indigo">ODPS v1.1</span>
            <span className="badge badge-indigo">OSI/Apache Ossie</span>
          </p>
        </div>

        {connected === null && <LoadingState caption="Checking your Snowflake connection…" />}
        {connected === false && (
          <div className="card callout connect-card">
            <div className="callout-icon" aria-hidden="true">
              <BrandMark />
            </div>
            <div className="callout-text">
              <h2>Connect to Snowflake to start</h2>
              <p className="muted">
                Context Builder reads your databases and saves data products with your own Snowflake account.
              </p>
            </div>
            <button type="button" className="btn-primary" onClick={() => setPanel('')}>
              Snowflake connection
            </button>
          </div>
        )}
        {/* Keyed by user so a different sign-in never sees the previous user's bundle. */}
        {connected && <Wizard key={user.username} />}
      </main>

      {footer}

      {panel !== null && (
        <ConnectionPanel reason={panel} onClose={() => setPanel(null)} onSaved={() => setConnected(true)} />
      )}
    </div>
  )
}
