import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Connection } from '../api'
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

/**
 * The signed-in user's Snowflake connection profile, in a modal dialog.
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
    api.getConnection().then(
      (connection) => {
        if (!live) return
        setValues({
          account: connection.account ?? '',
          user: connection.user ?? '',
          warehouse: connection.warehouse ?? '',
          role: connection.role ?? '',
          database: connection.database ?? '',
        })
        setPasswordSet(connection.passwordSet)
        setConnected(connection.connected)
        setError(connection.error ?? '')
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

        {reason && !connected && (
          <div className="banner banner-warn">
            <span className="banner-text">
              Context Builder reads your databases with your own Snowflake account. Enter and test your connection to
              continue.
              {`\n${reason}`}
            </span>
          </div>
        )}

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
            ) : loading ? null : (
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
