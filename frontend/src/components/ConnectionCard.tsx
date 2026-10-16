import { useEffect, useState } from 'react'
import { api } from '../api'
import { BrandMark } from './common'

interface Props {
  /** A saved, seeded or open connection: the wizard is usable. */
  ready: boolean
  /** Opens the connection panel. */
  onOpen: () => void
}

/**
 * The main page's word on the Snowflake connection: a slim "ready" line when there is one, otherwise
 * a card that says whether the server can prefill the form. Both link to the connection panel.
 */
export function ConnectionCard({ ready, onOpen }: Props) {
  // null while unknown; only read when nothing is saved.
  const [serverDefaults, setServerDefaults] = useState<boolean | null>(null)

  useEffect(() => {
    if (ready) {
      return
    }
    let live = true
    api.getConnectionDefaults().then(
      (defaults) => live && setServerDefaults(Object.values(defaults).some((v) => v)),
      () => live && setServerDefaults(false),
    )
    return () => {
      live = false
    }
  }, [ready])

  if (ready) {
    return (
      <p className="connection-ready">
        <span className="badge badge-ok">● ready</span>
        <span className="muted">Your Snowflake connection is set up.</span>
        <button type="button" className="btn-ghost btn-small" onClick={onOpen}>
          Snowflake connection
        </button>
      </p>
    )
  }

  return (
    <div className="card callout connect-card">
      <div className="callout-icon" aria-hidden="true">
        <BrandMark />
      </div>
      <div className="callout-text">
        <h2>Connect to Snowflake to start</h2>
        <p className="muted">
          {serverDefaults
            ? 'The form is filled in from the server’s settings: add your Snowflake password to continue.'
            : serverDefaults === false
              ? 'The server has no Snowflake defaults: enter your own account details to continue.'
              : 'Context Builder reads your databases with your own Snowflake account.'}
        </p>
      </div>
      <button type="button" className="btn-primary" onClick={onOpen}>
        Snowflake connection
      </button>
    </div>
  )
}
