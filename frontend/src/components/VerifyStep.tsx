import { useState } from 'react'
import { api } from '../api'
import type { Bundle, QualityRun } from '../api'
import { messageOf } from '../lib/util'
import { useElapsed } from '../lib/useElapsed'
import { ErrorBanner, Spinner } from './common'

/** Step 4: execute every DQ rule in the contracts against Snowflake and show the evidence. */
export function VerifyStep({ bundle }: { bundle: Bundle }) {
  const [run, setRun] = useState<QualityRun | null>(null)
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [onlyFailed, setOnlyFailed] = useState(false)
  const elapsed = useElapsed(startedAt)

  const ruleCount = bundle.contracts.reduce(
    (n, c) =>
      n + c.schema.reduce((m, o) => m + (o.quality?.length ?? 0) + o.properties.reduce((k, p) => k + (p.quality?.length ?? 0), 0), 0),
    0,
  )

  async function execute() {
    setStartedAt(Date.now())
    setError('')
    try {
      setRun(await api.runQuality(bundle))
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setStartedAt(null)
    }
  }

  const contractName = (id: string) => bundle.contracts.find((c) => c.id === id)?.name ?? id
  const rows = (run?.results ?? []).filter((r) => !onlyFailed || !r.passed)
  const total = run ? run.passed + run.failed : 0

  return (
    <section className="step">
      <div className="card hero-card">
        <div>
          <h2>Verify data quality</h2>
          <p className="muted">
            Runs the {ruleCount} quality rule{ruleCount === 1 ? '' : 's'} declared in the contracts as SQL on Snowflake.
            {ruleCount === 0 && ' Accept some DQ rules in Analyze first.'}
          </p>
        </div>
        <button type="button" className="btn-primary btn-large" disabled={startedAt !== null} onClick={() => void execute()}>
          {startedAt !== null ? <Spinner label={`Running… ${elapsed}s`} /> : run ? 'Run again' : 'Run quality rules'}
        </button>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError('')} />

      {run && (
        <>
          <div className="scoreboard">
            <div className="score score-ok">
              <span className="score-num">{run.passed}</span>
              <span className="score-label">passed</span>
            </div>
            <div className="score score-fail">
              <span className="score-num">{run.failed}</span>
              <span className="score-label">failed</span>
            </div>
            <div className="score">
              <span className="score-num">{total ? Math.round((run.passed / total) * 100) : 0}%</span>
              <span className="score-label">pass rate</span>
            </div>
            <span className="spacer" />
            <label className="check">
              <input type="checkbox" checked={onlyFailed} onChange={(e) => setOnlyFailed(e.target.checked)} />
              Only failures
            </label>
          </div>

          {run.results.length === 0 ? (
            <p className="muted">No rules were executed.</p>
          ) : (
            <table className="grid dq-table">
              <thead>
                <tr>
                  <th>Result</th>
                  <th>Contract · table · column</th>
                  <th>Rule</th>
                  <th>Observed</th>
                  <th>Threshold</th>
                  <th>SQL</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, i) => (
                  <tr key={i} className={r.passed ? '' : 'row-fail'}>
                    <td>
                      <span className={r.passed ? 'badge badge-ok' : 'badge badge-fail'}>{r.passed ? '✓ Pass' : r.error ? '! Error' : '✕ Fail'}</span>
                    </td>
                    <td>
                      <span className="muted">{contractName(r.contractId)}</span>
                      <br />
                      <span className="mono">
                        {r.table}
                        {r.column ? `.${r.column}` : ''}
                      </span>
                    </td>
                    <td>{r.rule}</td>
                    <td className="num">{r.observed ?? '—'}</td>
                    <td className="mono">{r.threshold}</td>
                    <td>
                      <pre className="sql">{r.sql}</pre>
                      {r.error && <p className="field-error">{r.error}</p>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  )
}
