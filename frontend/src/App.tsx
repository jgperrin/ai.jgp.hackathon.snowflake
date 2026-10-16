import { useEffect, useRef, useState } from 'react'
import { ApiError, MOCK, api, onSessionEvent } from './api'
import type { SessionUser } from './api'
import { AppearanceToggle } from './components/AppearanceToggle'
import { ConnectionCard } from './components/ConnectionCard'
import { ConnectionPanel } from './components/ConnectionPanel'
import { Footer } from './components/Footer'
import { HelpButton } from './components/HelpButton'
import { ProfileMenu } from './components/ProfileMenu'
import type { ConnectionStatus } from './components/ProfileMenu'
import { SignIn } from './components/SignIn'
import { BrandMark, LoadingState } from './components/common'
import { Wizard } from './Wizard'

/** undefined while the session is being checked, null when signed out. */
type Session = SessionUser | null | undefined

/** null while unknown (just signed in, being checked); true once a profile is saved, opened or not. */
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
  // True when no connection profile is saved at all (the menu says "Needs setup" rather than "Not connected").
  const [needsSetup, setNeedsSetup] = useState(false)
  // null: panel closed; a string: open, with the reason it opened on its own ('' when the user opened it).
  const [panel, setPanel] = useState<string | null>(null)
  // Who opened the panel, so closing it hands focus back instead of dropping it on <body> (DIND#78).
  const opener = useRef<HTMLElement | null>(null)
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

  // Once signed in, find out whether there is a connection. A saved or seeded profile opens on first use,
  // so only a missing one (or a failed open) brings the panel up on its own.
  const username = user?.username
  useEffect(() => {
    if (!username) {
      return
    }
    let live = true
    api.getConnection().then(
      (connection) => {
        if (!live) return
        setConnected(connection.state !== 'NONE')
        setNeedsSetup(connection.state === 'NONE')
        if (connection.state === 'NONE' || connection.error) {
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

  function openPanel() {
    const active = document.activeElement
    opener.current = active instanceof HTMLElement && active !== document.body ? active : null
    setPanel('')
  }

  function closePanel() {
    setPanel(null)
    const back = opener.current
    opener.current = null
    // After the panel unmounts. A menu item that opened it is gone by then; fall back to the menu button.
    requestAnimationFrame(() => {
      const target = back?.isConnected ? back : document.querySelector<HTMLElement>('.profile-menu > button')
      target?.focus()
    })
  }

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

  const connectionStatus: ConnectionStatus =
    connected === null ? null : connected ? 'connected' : needsSetup ? 'needs-setup' : 'not-connected'

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
            <AppearanceToggle />
            <HelpButton user={user.username} connected={connected} />
            {/* Rightmost (DIND#69): the Snowflake connection and Sign out live in the profile menu. */}
            <ProfileMenu
              username={user.username}
              status={connectionStatus}
              onOpenConnection={openPanel}
              onSignOut={() => void signOut()}
            />
          </div>
        </div>
      </header>

      <main className="content">
        <div className="page-header">
          <h1>From Snowflake schema to AI-ready data product</h1>
          <p className="standards" aria-label="Standards">
            <span className="badge badge-indigo">Bitol ODCS v3.2</span>
            <span className="badge badge-indigo">Bitol ODPS v1.1</span>
            <span className="badge badge-gray">OSI/Apache Ossie</span>
          </p>
        </div>

        {connected === null && <LoadingState caption="Checking your Snowflake connection…" />}
        {connected !== null && <ConnectionCard ready={connected} onOpen={openPanel} />}
        {/* Keyed by user so a different sign-in never sees the previous user's bundle. */}
        {connected && <Wizard key={user.username} />}
      </main>

      {footer}

      {panel !== null && (
        <ConnectionPanel reason={panel} onClose={closePanel} onSaved={() => setConnected(true)} />
      )}
    </div>
  )
}
