import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserRouter, Link, useLocation, useNavigate } from 'react-router-dom'
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
import { StoreView } from './components/StoreView'
import { AboutView } from './components/AboutView'
import poster from './assets/about/github2.webp'
import { BrandMark, LoadingState } from './components/common'
import { Wizard } from './Wizard'
import type { WizardHandle } from './Wizard'

/** undefined while the session is being checked, null when signed out. */
type Session = SessionUser | null | undefined

/** null while unknown (just signed in, being checked); true once a profile is saved, opened or not. */
type ConnectionState = boolean | null

const NOT_CONNECTED_YET = 'No Snowflake connection is saved for your profile yet.'

/**
 * The base path this app is served under, in the form react-router wants it: no trailing slash.
 * `import.meta.env.BASE_URL` is `/contextbuilder/` in a build and `/` in dev, so this is
 * `/contextbuilder` and `''` — an empty basename is what react-router expects at the root, and it
 * is what keeps one build working under either prefix (DIND#106).
 */
const BASENAME = import.meta.env.BASE_URL.replace(/\/$/, '')

/** Which view a path belongs to. The wizard is everything that is not one of the other two. */
type View = 'wizard' | 'store' | 'about'

function viewOf(pathname: string): View {
  if (pathname === '/store' || pathname.startsWith('/store/')) return 'store'
  return pathname === '/about' ? 'about' : 'wizard'
}

function pathOf(view: View): string {
  return view === 'wizard' ? '/' : `/${view}`
}

/**
 * The shell: sign-in gate, header with the signed-in user and the Snowflake
 * connection panel, then the wizard. Global API events (401, 409 NO_CONNECTION)
 * land here so no step has to handle them.
 */
export default function App() {
  return (
    <BrowserRouter basename={BASENAME}>
      <Shell />
    </BrowserRouter>
  )
}

function Shell() {
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
  // The store view sits over the wizard, which stays mounted (hidden) so a visit costs it nothing.
  // About (DIND#82) is the same kind of overlay: the wizard underneath keeps its state.
  //
  // Which of the three is showing comes from the URL, not from state (DIND#101): that is what makes
  // a view linkable, reloadable and reachable with Back. The wizard's own step is a path segment
  // too, read in Wizard.
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const view = viewOf(pathname)
  const setView = (next: View) => void navigate(pathOf(next))
  // /store/<saveId> is a shareable link to a saved product; StoreView opens it on arrival.
  const storeSaveId = pathname.startsWith('/store/') ? pathname.slice('/store/'.length) : ''
  const wizard = useRef<WizardHandle>(null)

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
          void navigate('/')
        } else {
          setPanel(event.message || NOT_CONNECTED_YET)
        }
      }),
    [navigate],
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
    setView('wizard')
  }

  /** Open on a save: load it into the wizard, then show it — where a loaded bundle lands. */
  // Stable identity, so the store's deep-link effect does not re-fire on every render.
  const openSave = useCallback(
    async (saveId: string) => {
      const bundle = await api.getProduct(saveId)
      if (!wizard.current) throw new Error('Connect to Snowflake first to open a save in the wizard.')
      await wizard.current.load(bundle)
      void navigate('/generate')
    },
    [navigate],
  )

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
          <Link className="topnav-brand" to="/" title="Back to start">
            <BrandMark />
            <span className="brand-name">Context Builder</span>
            <span className="brand-badge">v{__APP_VERSION__}</span>
          </Link>
          <span className="spacer" />
          <div className="account">
            <AppearanceToggle />
            <HelpButton user={user.username} connected={connected} />
            {/* A real link, so it can be middle-clicked and shared (DIND#101). */}
            <Link
              className={view === 'store' ? 'btn-secondary is-active' : 'btn-secondary'}
              aria-current={view === 'store' ? 'page' : undefined}
              to="/store"
            >
              Saved in Snowflake
            </Link>
            {/* Rightmost (DIND#69): the Snowflake connection and Sign out live in the profile menu. */}
            <ProfileMenu
              username={user.username}
              status={connectionStatus}
              onOpenConnection={openPanel}
              onAbout={() => setView('about')}
              onSignOut={() => void signOut()}
            />
          </div>
        </div>
      </header>

      {/* In the store view everything but the store pane is hidden by CSS, so the wizard stays mounted. */}
      <main
        className={view === 'wizard' ? 'content' : `content is-${view}`}
        // Imported, so the URL carries the /contextbuilder/ base in a build.
        style={view === 'about' ? { backgroundImage: `url(${poster})` } : undefined}
      >
        {view === 'about' && (
          <div className="about-pane">
            <AboutView onBack={() => setView('wizard')} />          </div>
        )}
        {view === 'store' && (
          <div className="store-pane">
            {/* Remounted when the connection changes, so saving one reloads the store it points at. */}
            <StoreView
              key={String(connected)}
              openId={storeSaveId}
              onOpen={openSave}
              onBack={() => setView('wizard')}
            />
          </div>
        )}
        <div className="page-header">
          <h1>From Snowflake schema to AI-ready data product</h1>
          <p className="standards" aria-label="Standards">
            {/* Linked (DIND#94): the same homes README.md:9 points at, plus Apache Ossie's. */}
            <a className="badge badge-indigo" href="https://github.com/bitol-io/open-data-contract-standard" target="_blank" rel="noreferrer">
              Bitol ODCS v3.2
            </a>
            <a className="badge badge-indigo" href="https://github.com/bitol-io/open-data-product-standard" target="_blank" rel="noreferrer">
              Bitol ODPS v1.1
            </a>
            <a className="badge badge-gray" href="https://github.com/apache/ossie" target="_blank" rel="noreferrer">
              OSI/Apache Ossie
            </a>
          </p>
        </div>

        {connected === null && <LoadingState caption="Checking your Snowflake connection…" />}
        {connected !== null && <ConnectionCard ready={connected} onOpen={openPanel} />}
        {/* Keyed by user so a different sign-in never sees the previous user's bundle. */}
        {connected && <Wizard key={user.username} ref={wizard} onBrowseStore={() => setView('store')} />}
      </main>

      {footer}

      {panel !== null && (
        <ConnectionPanel reason={panel} onClose={closePanel} onSaved={() => setConnected(true)} />
      )}
    </div>
  )
}
