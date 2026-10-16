import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Database, GenerateRequest, Schema, Table } from '../api'
import { formatCount, messageOf } from '../lib/util'
import { ErrorBanner, Spinner } from './common'

interface SchemaState {
  tables: Table[] | null
  loading: boolean
  error: string
  selected: Set<string>
  expanded: boolean
}

interface Props {
  busy: boolean
  onGenerate: (request: GenerateRequest) => void
}

/** Step 1: database → schemas (one contract each) → tables (all selected by default). */
export function SourceStep({ busy, onGenerate }: Props) {
  const [databases, setDatabases] = useState<Database[] | null>(null)
  const [database, setDatabase] = useState('')
  const [schemas, setSchemas] = useState<Schema[] | null>(null)
  const [picked, setPicked] = useState<Record<string, SchemaState>>({})
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('')
  const [showSystem, setShowSystem] = useState(false)

  useEffect(() => {
    api.listDatabases().then(setDatabases, (cause) => setError(messageOf(cause)))
  }, [])

  async function chooseDatabase(name: string) {
    setDatabase(name)
    setSchemas(null)
    setPicked({})
    setError('')
    if (!name) {
      return
    }
    try {
      setSchemas(await api.listSchemas(name))
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  function patch(schema: string, change: Partial<SchemaState>) {
    setPicked((prev) => (prev[schema] ? { ...prev, [schema]: { ...prev[schema], ...change } } : prev))
  }

  async function loadTables(schema: string) {
    patch(schema, { loading: true, error: '' })
    try {
      const tables = await api.listTables(database, schema)
      patch(schema, { tables, loading: false, selected: new Set(tables.map((t) => t.name)) })
    } catch (cause) {
      patch(schema, { loading: false, error: messageOf(cause) })
    }
  }

  function toggleSchema(schema: string) {
    if (picked[schema]) {
      setPicked(({ [schema]: _removed, ...rest }) => rest)
      return
    }
    setPicked((prev) => ({
      ...prev,
      [schema]: { tables: null, loading: true, error: '', selected: new Set(), expanded: false },
    }))
    void loadTables(schema)
  }

  function toggleTable(schema: string, table: string) {
    const state = picked[schema]
    const selected = new Set(state.selected)
    if (selected.has(table)) {
      selected.delete(table)
    } else {
      selected.add(table)
    }
    patch(schema, { selected })
  }

  function setAll(schema: string, on: boolean) {
    const state = picked[schema]
    patch(schema, { selected: new Set(on ? (state.tables ?? []).map((t) => t.name) : []) })
  }

  // The picked database stays listed even when the toggle or the filter would hide it, so hiding a
  // row never silently drops the selection (and the group keeps its checked radio as the tab stop).
  const needle = filter.trim().toLowerCase()
  const matches = (d: Database) =>
    !needle || d.name.toLowerCase().includes(needle) || (d.comment ?? '').toLowerCase().includes(needle)
  const allDatabases = databases ?? []
  const systemCount = allDatabases.filter((d) => d.system).length
  const shownDatabases = allDatabases.filter(
    (d) => d.name === database || (matches(d) && (showSystem || !d.system)),
  )
  const anyMatch = allDatabases.some((d) => matches(d) && (showSystem || !d.system))

  const chosen = (schemas ?? [])
    .filter((s) => picked[s.name]?.tables && picked[s.name].selected.size > 0)
    .map((s) => ({ schema: s.name, tables: [...picked[s.name].selected] }))
  const tableCount = chosen.reduce((n, s) => n + s.tables.length, 0)
  const stillLoading = Object.values(picked).some((s) => s.loading)

  return (
    <section className="step">
      <ErrorBanner error={error} onDismiss={() => setError('')} />

      <div className="card">
        <div className="field-label" id="database-label">
          Snowflake database <span className="hint">becomes one ODPS data product</span>
        </div>
        {databases === null && !error ? (
          <Spinner label="Reading catalog…" />
        ) : (
          <>
            <div className="db-tools">
              <input
                type="text"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                placeholder="Filter databases…"
                aria-label="Filter databases"
              />
              {systemCount > 0 && (
                <label className="check">
                  <input type="checkbox" checked={showSystem} onChange={(e) => setShowSystem(e.target.checked)} />
                  <span>
                    Show system databases ({systemCount} {showSystem ? 'shown' : 'hidden'})
                  </span>
                </label>
              )}
            </div>
            {allDatabases.length === 0 && <p className="muted">No databases visible to this connection.</p>}
            {allDatabases.length > 0 && !anyMatch && (
              <p className="muted">
                {needle ? `No databases match “${filter.trim()}”.` : 'Only system databases here.'}
                {!showSystem && systemCount > 0 && ' System databases are hidden.'}
              </p>
            )}
            <ul className="schema-list db-list" role="radiogroup" aria-labelledby="database-label">
              {shownDatabases.map((d) => {
                const classes = ['schema-item']
                if (d.name === database) classes.push('is-picked')
                if (d.system) classes.push('is-system')
                return (
                  <li key={d.name} className={classes.join(' ')}>
                    <label className="check schema-row">
                      <input
                        type="radio"
                        name="database"
                        value={d.name}
                        checked={d.name === database}
                        onChange={() => void chooseDatabase(d.name)}
                        onKeyDown={(e) => {
                          // Native radios pick on arrows and Space; Enter picks too, as the issue asks.
                          if (e.key === 'Enter' && d.name !== database) {
                            e.preventDefault()
                            void chooseDatabase(d.name)
                          }
                        }}
                      />
                      <span className="schema-name">{d.name}</span>
                      {d.comment && <span className="muted schema-comment">{d.comment}</span>}
                      {d.system && <span className="pill">system</span>}
                    </label>
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </div>

      {database && (
        <div className="card">
          <div className="field-label">
            Schemas <span className="hint">each ticked schema becomes one ODCS contract = one output port</span>
          </div>
          {schemas === null && !error && <Spinner label="Listing schemas…" />}
          {schemas?.length === 0 && <p className="muted">No schemas visible in {database}.</p>}
          <ul className="schema-list">
            {(schemas ?? []).map((s) => {
              const state = picked[s.name]
              const total = state?.tables?.length ?? 0
              return (
                <li key={s.name} className={state ? 'schema-item is-picked' : 'schema-item'}>
                  <div className="schema-row">
                    <label className="check">
                      <input type="checkbox" checked={!!state} onChange={() => toggleSchema(s.name)} />
                      <span className="schema-name">{s.name}</span>
                    </label>
                    {s.comment && <span className="muted schema-comment">{s.comment}</span>}
                    <span className="spacer" />
                    {state?.loading && <Spinner />}
                    {state?.tables && (
                      <>
                        <span className={state.selected.size ? 'pill' : 'pill pill-warn'}>
                          {state.selected.size}/{total} tables
                        </span>
                        <button
                          type="button"
                          className="btn-ghost btn-small"
                          aria-expanded={state.expanded}
                          onClick={() => patch(s.name, { expanded: !state.expanded })}
                        >
                          {state.expanded ? 'Hide tables ▴' : 'Choose tables ▾'}
                        </button>
                      </>
                    )}
                  </div>
                  {state?.error && <ErrorBanner error={state.error} />}
                  {state?.tables && state.expanded && (
                    <div className="table-list">
                      <div className="table-list-actions">
                        <button type="button" className="btn-ghost btn-small" onClick={() => setAll(s.name, true)}>
                          Select all
                        </button>
                        <button type="button" className="btn-ghost btn-small" onClick={() => setAll(s.name, false)}>
                          Select none
                        </button>
                      </div>
                      {state.tables.length === 0 && <p className="muted">No tables or views.</p>}
                      {state.tables.map((t) => (
                        <label key={t.name} className="check table-row">
                          <input
                            type="checkbox"
                            checked={state.selected.has(t.name)}
                            onChange={() => toggleTable(s.name, t.name)}
                          />
                          <span className="mono">{t.name}</span>
                          <span className={t.kind === 'VIEW' ? 'kind kind-view' : 'kind'}>{t.kind}</span>
                          <span className="muted">{t.columns.length} cols</span>
                          <span className="muted rows">{formatCount(t.rowCount)} rows</span>
                          {t.comment && <span className="muted table-comment">{t.comment}</span>}
                        </label>
                      ))}
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <div className="actions">
        <button
          type="button"
          className="btn-primary btn-large"
          disabled={busy || stillLoading || chosen.length === 0}
          onClick={() => onGenerate({ database, schemas: chosen })}
        >
          {busy ? (
            <Spinner label="Generating…" />
          ) : (
            `Generate data product · ${chosen.length} contract${chosen.length === 1 ? '' : 's'} · ${tableCount} table${tableCount === 1 ? '' : 's'}`
          )}
        </button>
      </div>
    </section>
  )
}
