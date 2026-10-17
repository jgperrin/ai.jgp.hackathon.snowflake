import { Fragment, useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import type { SavedArtifact, SavedSave } from '../api'
import { downloadText, messageOf } from '../lib/util'
import { CopyButton, ErrorBanner, Spinner } from './common'

interface Props {
  /** Loads a save into the wizard (and shows it); rejects with the reason it could not. */
  onOpen: (saveId: string) => Promise<void>
  onBack: () => void
  /** A save id from the URL (/store/<id>), opened on arrival (DIND#101). */
  openId?: string
}

const KIND_LABEL: Record<string, string> = { ODPS: 'ODPS product', ODCS: 'ODCS contract', OSI: 'OSI model' }
const EXTENSION: Record<string, string> = { ODPS: 'odps', ODCS: 'odcs', OSI: 'osi' }

/** Everything saved in Snowflake: one row per save, expandable to its artifacts and their stored YAML. */
export function StoreView({ onOpen, onBack, openId = '' }: Props) {
  const [saves, setSaves] = useState<SavedSave[] | null>(null)
  const [database, setDatabase] = useState<string | null>(null)
  const [expanded, setExpanded] = useState('')
  // Artifacts per save id, fetched on first expansion.
  const [artifacts, setArtifacts] = useState<Record<string, SavedArtifact[]>>({})
  const [selected, setSelected] = useState<{ saveId: string; index: number } | null>(null)
  const [loadingId, setLoadingId] = useState('')
  const [error, setError] = useState('')
  // The save whose delete is waiting on a confirmation, and the one being deleted right now.
  const [confirming, setConfirming] = useState('')
  const [deleting, setDeleting] = useState('')
  // True when the list could not be read, so an empty table is not mistaken for an empty store.
  const [failed, setFailed] = useState(false)

  const load = useCallback(
    () =>
      api.listSaves().then(
        (list) => {
          setFailed(false)
          setSaves(list)
        },
        (cause: unknown) => {
          setFailed(true)
          setSaves([])
          setError(messageOf(cause))
        },
      ),
    [],
  )

  function refresh() {
    setSaves(null)
    setExpanded('')
    setArtifacts({})
    setSelected(null)
    void load()
  }

  useEffect(() => {
    void load()
    // The store lives in the database of the user's own connection profile.
    api.getConnection().then(
      (connection) => setDatabase(connection.database),
      () => setDatabase(null),
    )
  }, [load])

  // A save id in the URL opens that save. A bad one is reported here and leaves the user in the
  // store, which is what the reader of that link needs to know (DIND#101).
  useEffect(() => {
    if (!openId) {
      return
    }
    let live = true
    onOpen(openId).catch((cause: unknown) => {
      if (live) {
        setError(messageOf(cause))
      }
    })
    return () => {
      live = false
    }
  }, [openId, onOpen])

  async function toggle(saveId: string) {
    if (expanded === saveId) {
      setExpanded('')
      return
    }
    setExpanded(saveId)
    if (artifacts[saveId]) return
    try {
      const list = await api.getSaveArtifacts(saveId)
      setArtifacts((all) => ({ ...all, [saveId]: list }))
    } catch (cause) {
      setExpanded('')
      setError(messageOf(cause))
    }
  }

  async function open(saveId: string) {
    setLoadingId(saveId)
    setError('')
    try {
      await onOpen(saveId)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setLoadingId('')
    }
  }

  /** Delete one save, whole. The list is re-read from the store afterwards rather than patched by hand. */
  async function remove(saveId: string) {
    setDeleting(saveId)
    setError('')
    try {
      await api.deleteSave(saveId)
      setConfirming('')
      setArtifacts((all) => {
        const rest = { ...all }
        delete rest[saveId]
        return rest
      })
      if (expanded === saveId) {
        setExpanded('')
      }
      if (selected?.saveId === saveId) {
        setSelected(null)
      }
      await load()
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setDeleting('')
    }
  }

  const table = `${database || '<database>'}.DATA_PRODUCTS.ARTIFACTS`
  const shown = selected ? artifacts[selected.saveId]?.[selected.index] : undefined
  const shownFile = shown ? `${shown.artifactId}.${EXTENSION[shown.kind] ?? shown.kind.toLowerCase()}.yaml` : ''

  return (
    <>
      <div className="page-header">
        <h1>Saved in Snowflake</h1>
        <p className="muted store-subline">
          Every save, newest first, as stored in <span className="mono">{table}</span>.
        </p>
      </div>

      <section className="step">
        <ErrorBanner error={error} onDismiss={() => setError('')} />

        <div className="card">
          <div className="table-group-head">
            <h2>Saves</h2>
            <span className="spacer" />
            <button type="button" className="btn-ghost btn-small" onClick={refresh}>
              Refresh
            </button>
            <button type="button" className="btn-secondary btn-small" onClick={onBack}>
              ← Back to the wizard
            </button>
          </div>
          {saves === null ? (
            <Spinner label="Loading…" />
          ) : failed ? (
            <p className="muted">
              Could not read <span className="mono">{table}</span>. Check your Snowflake connection, then Refresh.
            </p>
          ) : saves.length === 0 ? (
            <p className="muted">
              Nothing has been saved to <span className="mono">{table}</span> yet. Save a product in step 5, Publish,
              and it appears here.
            </p>
          ) : (
            <table className="grid store-grid">
              <thead>
                <tr>
                  <th aria-label="Expand" />
                  <th>Product</th>
                  <th>Id</th>
                  <th>Version</th>
                  <th>Saved</th>
                  <th>Saved by</th>
                  <th>Artifacts</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {saves.map((s) => {
                  const isOpen = expanded === s.saveId
                  const list = artifacts[s.saveId]
                  return (
                    <Fragment key={s.saveId}>
                      <tr className={isOpen ? 'is-expanded' : undefined}>
                        <td>
                          <button
                            type="button"
                            className="btn-ghost btn-small store-toggle"
                            aria-expanded={isOpen}
                            aria-label={isOpen ? 'Hide artifacts' : 'Show artifacts'}
                            onClick={() => void toggle(s.saveId)}
                          >
                            {isOpen ? '▾' : '▸'}
                          </button>
                        </td>
                        <td>{s.name ?? <span className="muted">no product</span>}</td>
                        <td className="mono">{s.productId}</td>
                        <td className="mono">{s.version ?? '—'}</td>
                        <td>{new Date(s.savedAt).toLocaleString()}</td>
                        <td>{s.savedBy ?? '—'}</td>
                        <td>{s.artifacts.length}</td>
                        <td>
                          <button
                            type="button"
                            className="btn-ghost btn-small"
                            disabled={loadingId !== '' || s.name === null}
                            onClick={() => void open(s.saveId)}
                          >
                            {loadingId === s.saveId ? <Spinner /> : 'Open'}
                          </button>
                          <button
                            type="button"
                            className="btn-danger btn-small"
                            disabled={deleting !== ''}
                            onClick={() => setConfirming(confirming === s.saveId ? '' : s.saveId)}
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                      {confirming === s.saveId && (
                        <tr className="store-confirm">
                          <td />
                          <td colSpan={7}>
                            <div className="store-confirm-inner">
                              <span className="store-confirm-text">
                                Delete <strong>{s.name ?? 'this save'}</strong> v{s.version ?? '—'}, saved{' '}
                                {new Date(s.savedAt).toLocaleString()} by {s.savedBy ?? '—'}? Its {s.artifacts.length}{' '}
                                artifact{s.artifacts.length === 1 ? '' : 's'} go with it, and this cannot be undone.
                              </span>
                              <span className="spacer" />
                              <button
                                type="button"
                                className="btn-danger btn-small"
                                disabled={deleting !== ''}
                                onClick={() => void remove(s.saveId)}
                              >
                                {deleting === s.saveId ? <Spinner /> : 'Delete for good'}
                              </button>
                              <button
                                type="button"
                                className="btn-ghost btn-small"
                                disabled={deleting !== ''}
                                onClick={() => setConfirming('')}
                              >
                                Cancel
                              </button>
                            </div>
                          </td>
                        </tr>
                      )}
                      {isOpen && (
                        <tr className="store-artifacts">
                          <td />
                          <td colSpan={7}>
                            {!list ? (
                              <Spinner label="Loading artifacts…" />
                            ) : (
                              <table className="grid">
                                <thead>
                                  <tr>
                                    <th>Kind</th>
                                    <th>Name</th>
                                    <th>Version</th>
                                    <th>Status</th>
                                    <th />
                                  </tr>
                                </thead>
                                <tbody>
                                  {list.map((a, index) => {
                                    const isShown = selected?.saveId === s.saveId && selected.index === index
                                    return (
                                      <tr key={`${a.kind}-${a.artifactId}-${index}`}>
                                        <td>
                                          <span className="badge badge-indigo">{KIND_LABEL[a.kind] ?? a.kind}</span>
                                        </td>
                                        <td>{a.name ?? a.artifactId}</td>
                                        <td className="mono">{a.version ?? '—'}</td>
                                        <td>{a.status ?? '—'}</td>
                                        <td>
                                          <button
                                            type="button"
                                            className={isShown ? 'btn-secondary btn-small' : 'btn-ghost btn-small'}
                                            aria-pressed={isShown}
                                            onClick={() => setSelected(isShown ? null : { saveId: s.saveId, index })}
                                          >
                                            {isShown ? 'Hide YAML' : 'View YAML'}
                                          </button>
                                        </td>
                                      </tr>
                                    )
                                  })}
                                </tbody>
                              </table>
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>

        {shown && (
          <div className="code-panel">
            <div className="code-toolbar">
              <span className="mono muted">{shownFile}</span>
              <span className="spacer" />
              {shown.yaml !== null && (
                <>
                  <CopyButton text={shown.yaml} />
                  <button type="button" className="btn-secondary" onClick={() => downloadText(shownFile, shown.yaml ?? '')}>
                    Download
                  </button>
                </>
              )}
            </div>
            {shown.yaml === null ? (
              <p className="code-error">This artifact was stored without YAML.</p>
            ) : (
              <pre className="code">{shown.yaml}</pre>
            )}
          </div>
        )}
      </section>
    </>
  )
}
