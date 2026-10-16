import { useState } from 'react'
import type { Bundle, BundleYaml } from '../api'
import { downloadText } from '../lib/util'
import { CopyButton, Spinner } from './common'

interface Props {
  bundle: Bundle
  yaml: BundleYaml | null
  yamlError: string
}

/** Validation badge + error list, then the product and each contract as YAML tabs. */
export function DocumentsView({ bundle, yaml, yamlError }: Props) {
  const [tab, setTab] = useState('product')
  const { validation } = bundle

  const tabs = [
    { key: 'product', label: bundle.product.name || bundle.product.id, kind: 'ODPS v1.1', file: `${bundle.product.id}.odps.yaml` },
    ...bundle.contracts.map((c) => ({ key: c.id, label: c.name || c.id, kind: 'ODCS v3.2', file: `${c.id}.odcs.yaml` })),
  ]
  const current = tabs.find((t) => t.key === tab) ?? tabs[0]
  const text = yaml ? (current.key === 'product' ? yaml.product : yaml.contracts[current.key]) : undefined
  const errorsFor = (key: string) =>
    validation.errors.filter((e) => e.document === key || (key === 'product' && e.document === bundle.product.id)).length

  return (
    <div className="documents">
      <div className={validation.valid ? 'banner banner-ok' : 'banner banner-error'} role="status">
        <span className={validation.valid ? 'badge badge-ok' : 'badge badge-fail'}>
          {validation.valid ? '✓ Valid' : `✕ ${validation.errors.length} validation error${validation.errors.length === 1 ? '' : 's'}`}
        </span>
        <span className="banner-text">
          {validation.valid
            ? `ODPS product and ${bundle.contracts.length} ODCS contract${bundle.contracts.length === 1 ? '' : 's'} pass schema validation.`
            : 'The generated documents do not pass schema validation:'}
        </span>
      </div>
      {!validation.valid && (
        <table className="grid validation-errors">
          <thead>
            <tr>
              <th>Document</th>
              <th>Path</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            {validation.errors.map((e, i) => (
              <tr key={i}>
                <td className="mono">{e.document}</td>
                <td className="mono">{e.path}</td>
                <td>{e.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="tabs" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={t.key === current.key}
            className={t.key === current.key ? 'tab is-active' : 'tab'}
            onClick={() => setTab(t.key)}
          >
            <span className="tab-kind">{t.kind}</span>
            <span className="tab-label">{t.label}</span>
            {errorsFor(t.key) > 0 && <span className="tab-err">{errorsFor(t.key)}</span>}
          </button>
        ))}
      </div>

      <div className="code-panel">
        <div className="code-toolbar">
          <span className="mono muted">{current.file}</span>
          <span className="spacer" />
          {text !== undefined && (
            <>
              <CopyButton text={text} />
              <button type="button" className="btn-secondary" onClick={() => downloadText(current.file, text)}>
                Download
              </button>
            </>
          )}
        </div>
        {yamlError ? (
          <p className="code-error">{yamlError}</p>
        ) : text === undefined ? (
          <div className="code-loading">
            <Spinner label="Rendering YAML…" />
          </div>
        ) : (
          <pre className="code">{text}</pre>
        )}
      </div>

      {yaml && (
        <div className="actions">
          <button
            type="button"
            className="btn-ghost"
            onClick={() => tabs.forEach((t) => {
              const body = t.key === 'product' ? yaml.product : yaml.contracts[t.key]
              if (body !== undefined) downloadText(t.file, body)
            })}
          >
            Download all ({tabs.length} files)
          </button>
        </div>
      )}
    </div>
  )
}
