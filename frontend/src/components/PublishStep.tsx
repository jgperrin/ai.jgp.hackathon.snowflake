import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import type { Bundle, OsiExport, SaveResult, SavedProduct } from '../api'
import { downloadText, messageOf } from '../lib/util'
import { CopyButton, ErrorBanner, Spinner } from './common'

interface Props {
  bundle: Bundle
  onLoad: (bundle: Bundle) => Promise<void>
}

/** Step 5: persist the product in Snowflake and export it to OSI/Apache Ossie. */
export function PublishStep({ bundle, onLoad }: Props) {
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState<SaveResult | null>(null)
  const [exporting, setExporting] = useState(false)
  const [osi, setOsi] = useState<OsiExport | null>(null)
  const [products, setProducts] = useState<SavedProduct[] | null>(null)
  const [loadingId, setLoadingId] = useState('')
  const [error, setError] = useState('')

  const refresh = useCallback(
    () =>
      api.listProducts().then(setProducts, (cause: unknown) => {
        setProducts([])
        setError(messageOf(cause))
      }),
    [],
  )

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function save() {
    setSaving(true)
    setError('')
    try {
      setSaved(await api.save(bundle))
      await refresh()
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setSaving(false)
    }
  }

  async function exportOsi() {
    setExporting(true)
    setError('')
    try {
      setOsi(await api.exportOsi(bundle))
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setExporting(false)
    }
  }

  async function load(id: string) {
    setLoadingId(id)
    setError('')
    try {
      await onLoad(await api.getProduct(id))
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setLoadingId('')
    }
  }

  const osiFile = `${bundle.product.id}.osi.yaml`

  return (
    <section className="step">
      <ErrorBanner error={error} onDismiss={() => setError('')} />

      <div className="publish-grid">
        <div className="card">
          <h2>Save to Snowflake</h2>
          <p className="muted">
            Stores the ODPS product and its {bundle.contracts.length} ODCS contract{bundle.contracts.length === 1 ? '' : 's'}{' '}
            in Snowflake, versioned.
          </p>
          {!bundle.validation.valid && (
            <p className="field-error">
              Heads-up: the bundle has {bundle.validation.errors.length} validation error
              {bundle.validation.errors.length === 1 ? '' : 's'}.
            </p>
          )}
          <div className="actions">
            <button type="button" className="btn-primary" disabled={saving} onClick={() => void save()}>
              {saving ? <Spinner label="Saving…" /> : 'Save to Snowflake'}
            </button>
          </div>
          {saved && (
            <div className="banner banner-ok" role="status">
              <span className="badge badge-ok">✓ Saved</span>
              <span className="banner-text">
                <span className="mono">{saved.id}</span> v{saved.version} · {new Date(saved.savedAt).toLocaleString()}
              </span>
            </div>
          )}
        </div>

        <div className="card">
          <h2>Export OSI/Apache Ossie</h2>
          <p className="muted">Turns the product into an OSI/Apache Ossie semantic model, ready to view or download.</p>
          <div className="actions">
            <button type="button" className="btn-primary" disabled={exporting} onClick={() => void exportOsi()}>
              {exporting ? <Spinner label="Exporting…" /> : 'Export OSI/Apache Ossie'}
            </button>
          </div>
        </div>
      </div>

      {osi && (
        <div className="documents">
          <div className={osi.valid ? 'banner banner-ok' : 'banner banner-error'} role="status">
            <span className={osi.valid ? 'badge badge-ok' : 'badge badge-fail'}>
              {osi.valid ? '✓ Valid' : `✕ ${osi.errors.length} error${osi.errors.length === 1 ? '' : 's'}`}
            </span>
            <span className="banner-text">
              {osi.valid ? 'OSI/Apache Ossie export is valid.' : osi.errors.join('\n')}
            </span>
          </div>
          <div className="code-panel">
            <div className="code-toolbar">
              <span className="mono muted">{osiFile}</span>
              <span className="spacer" />
              <CopyButton text={osi.yaml} />
              <button type="button" className="btn-secondary" onClick={() => downloadText(osiFile, osi.yaml)}>
                Download
              </button>
            </div>
            <pre className="code">{osi.yaml}</pre>
          </div>
        </div>
      )}

      <div className="card">
        <div className="table-group-head">
          <h2>Saved products</h2>
          <span className="spacer" />
          <button type="button" className="btn-ghost btn-small" onClick={() => void refresh()}>
            Refresh
          </button>
        </div>
        {products === null ? (
          <Spinner label="Loading…" />
        ) : products.length === 0 ? (
          <p className="muted">Nothing saved yet.</p>
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>Product</th>
                <th>Id</th>
                <th>Version</th>
                <th>Saved</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={`${p.id}-${p.version}`}>
                  <td>{p.name}</td>
                  <td className="mono">{p.id}</td>
                  <td className="mono">{p.version}</td>
                  <td>{new Date(p.savedAt).toLocaleString()}</td>
                  <td>
                    <button type="button" className="btn-ghost btn-small" disabled={loadingId !== ''} onClick={() => void load(p.id)}>
                      {loadingId === p.id ? <Spinner /> : 'Open'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  )
}
