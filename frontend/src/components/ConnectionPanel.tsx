import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Connection, ConnectionDefaults, ConnectionState } from '../api'
import { messageOf } from '../lib/util'
import { Spinner } from './common'

interface Props {
  /** Why the panel opened on its own (a 409 NO_CONNECTION), shown above the form; empty when the user opened it. */
  reason: string
  onClose: () => void
  onSaved: (connection: Connection) => void
}

type Field = 'account' | 'user' | 'warehouse' | 'role' | 'database'

const FIELDS: { key: Field; label: string; hint: string; placeholder: string }[] = [
  { key: 'account', label: 'Account', hint: 'account identifier', placeholder: 'myorg-myaccount' },
  { key: 'user', label: 'User', hint: '', placeholder: 'JDOE' },
  { key: 'warehouse', label: 'Warehouse', hint: '', placeholder: 'COMPUTE_WH' },
  { key: 'role', label: 'Role', hint: '', placeholder: 'SYSADMIN' },
  { key: 'database', label: 'Database', hint: 'where products are saved', placeholder: 'CONTEXT_BUILDER' },
]

const blank: Record<Field, string> = { account: '', user: '', warehouse: '', role: '', database: '' }

function fieldsOf(source: Connection | ConnectionDefaults): Record<Field, string> {
  return {
    account: source.account ?? '',
    user: source.user ?? '',
    warehouse: source.warehouse ?? '',
    role: source.role ?? '',
    database: source.database ?? '',
  }
}

/**
 * The signed-in user's Snowflake connection profile, in a modal dialog.
 * The five fields show the saved profile or, with nothing saved, the server's defaults.
 * The password is write-only: never pre-filled, and blank on save keeps the saved one.
 */
export function ConnectionPanel({ reason, onClose, onSaved }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [values, setValues] = useState<Record<Field, string>>(blank)
  const [password, setPassword] = useState('')
  const [passwordSet, setPasswordSet] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  // Status line: connected, or Snowflake's error text.
  const [connected, setConnected] = useState(false)
  const [state, setState] = useState<ConnectionState>('NONE')
  // True when the empty form was filled in from the server's defaults.
  const [prefilled, setPrefilled] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const element = dialog.current
    if (element && !element.open) {
      element.showModal()
    }
    return () => element?.close()
  }, [])

  useEffect(() => {
    let live = true
    async function load() {
      const connection = await api.getConnection()
      if (connection.state !== 'NONE') {
        return { connection, values: fieldsOf(connection), prefilled: false }
      }
      // Nothing saved: start from the server's SNOWFLAKE_* values, when it has any. A failed read is just an empty form.
      const defaults = await api.getConnectionDefaults().catch(() => null)
      const hasDefaults = !!defaults && Object.values(defaults).some((v) => v)
      return { connection, values: hasDefaults ? fieldsOf(defaults) : blank, prefilled: hasDefaults }
    }
    load().then(
      (loaded) => {
        if (!live) return
        setValues(loaded.values)
        setPrefilled(loaded.prefilled)
        setPasswordSet(loaded.connection.passwordSet)
        setState(loaded.connection.state)
        setConnected(loaded.connection.connected)
        setError(loaded.connection.error ?? '')
        setLoading(false)
      },
      (cause) => {
        if (!live) return
        setError(messageOf(cause))
        setLoading(false)
      },
    )
    return () => {
      live = false
    }
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setConnected(false)
    try {
      const saved = await api.saveConnection({ ...values, ...(password ? { password } : {}) })
      setPassword('')
      setPasswordSet(saved.passwordSet)
      setState(saved.state)
      setPrefilled(false)
      setConnected(saved.connected)
      setError(saved.error ?? '')
      if (saved.connected) {
        onSaved(saved)
      }
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setSaving(false)
    }
  }

  return (
    // Escape fires 'cancel'; route it through onClose so the parent's state stays the source of truth.
    <dialog
      ref={dialog}
      className="panel"
      aria-labelledby="connection-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
    >
      <form className="panel-body" onSubmit={(event) => void submit(event)}>
        <div className="panel-head">
          <h2 id="connection-title">Snowflake connection</h2>
          <span className="spacer" />
          <button type="button" className="btn-ghost btn-small" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        {!loading && !connected && <p className="muted connection-notice">{notice(state, prefilled, reason)}</p>}

        {loading ? (
          <Spinner label="Loading your connection…" />
        ) : (
          <div className="panel-fields">
            {FIELDS.slice(0, 2).map((f) => (
              <TextField key={f.key} field={f} value={values[f.key]} onChange={(v) => setValues({ ...values, [f.key]: v })} />
            ))}
            <label className="form-field">
              <span className="field-label">
                Password
                {passwordSet && <span className="hint">saved</span>}
              </span>
              <input
                type="password"
                name="snowflake-password"
                autoComplete="new-password"
                placeholder={passwordSet ? 'saved — leave blank to keep' : ''}
                required={!passwordSet}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            {FIELDS.slice(2).map((f) => (
              <TextField key={f.key} field={f} value={values[f.key]} onChange={(v) => setValues({ ...values, [f.key]: v })} />
            ))}
          </div>
        )}

        <div className="actions">
          <button type="submit" className="btn-primary" disabled={loading || saving}>
            {saving ? <Spinner label="Testing…" /> : 'Test & save'}
          </button>
          <span className="connection-status" role="status" aria-live="polite">
            {saving ? null : connected ? (
              <span className="badge badge-ok">Connected ✓</span>
            ) : error ? (
              <span className="status-error">{error}</span>
            ) : loading ? null : state === 'SAVED' ? (
              <span className="badge badge-gray">Saved · opens on first use</span>
            ) : (
              <span className="muted">Not connected</span>
            )}
          </span>
          <span className="spacer" />
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </form>
    </dialog>
  )
}

/** The panel's one line: why it is here and what is left to do. */
function notice(state: ConnectionState, prefilled: boolean, reason: string): string {
  if (state === 'SAVED') {
    return reason || 'Your connection is saved and opens when you first use it. Change anything and Test & save.'
  }
  if (prefilled) {
    return 'Filled in from the server’s settings: add your Snowflake password, then Test & save.'
  }
  return reason || 'Enter your Snowflake account details, then Test & save.'
}

function TextField({
  field,
  value,
  onChange,
}: {
  field: (typeof FIELDS)[number]
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="form-field">
      <span className="field-label">
        {field.label}
        {field.hint && <span className="hint">{field.hint}</span>}
      </span>
      <input
        type="text"
        name={`snowflake-${field.key}`}
        autoComplete="off"
        spellCheck={false}
        required={field.key === 'account' || field.key === 'user'}
        placeholder={field.placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}
