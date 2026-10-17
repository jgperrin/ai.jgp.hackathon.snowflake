import { useState } from 'react'
import { api } from '../api'
import type { Bundle, Proposal, ProposalKind } from '../api'
import { messageOf } from '../lib/util'
import { useElapsed } from '../lib/useElapsed'
import { ErrorBanner, Spinner } from './common'

type Status = 'pending' | 'accepted' | 'rejected' | 'applied'

interface Decision {
  status: Status
  /** The value that will be applied: the proposal's own, or the user's edit. */
  value: unknown
  edited: boolean
}

interface Props {
  bundle: Bundle
  onApplied: (bundle: Bundle) => Promise<void>
  onViewDocuments: () => void
}

const KIND_LABEL: Record<ProposalKind, string> = {
  primaryKey: 'Primary key',
  description: 'Description',
  businessName: 'Business name',
  classification: 'Classification',
  tag: 'Tag',
  qualityRule: 'DQ rule',
  sla: 'SLA',
}

/**
 * The order the review reads in, whatever order Cortex answered in: the things that describe a
 * table, then the things that constrain it. A kind this list does not know about is appended
 * after these, so a new proposal kind can never make its cards disappear.
 */
const SECTION_ORDER: ProposalKind[] = [
  'primaryKey',
  'description',
  'businessName',
  'classification',
  'tag',
  'qualityRule',
  'sla',
]

interface Section {
  kind: string
  items: Proposal[]
}

/** One section per category, in SECTION_ORDER, then any kind that is not in it. */
function sectionsOf(items: Proposal[]): Section[] {
  const kinds = [...SECTION_ORDER, ...new Set(items.map((p) => p.kind as string))]
  return [...new Set(kinds)]
    .map((kind) => ({ kind, items: items.filter((p) => p.kind === kind) }))
    .filter((section) => section.items.length > 0)
}

const isText = (kind: ProposalKind) => kind !== 'qualityRule' && kind !== 'sla'

/** Step 3: Cortex proposes, the human decides. Only explicitly accepted proposals are ever sent to /apply. */
export function AnalyzeStep({ bundle, onApplied, onViewDocuments }: Props) {
  const [proposals, setProposals] = useState<Proposal[] | null>(null)
  const [warnings, setWarnings] = useState<string[]>([])
  const [decisions, setDecisions] = useState<Record<string, Decision>>({})
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [applying, setApplying] = useState(false)
  const [appliedCount, setAppliedCount] = useState(0)
  const [error, setError] = useState('')
  const elapsed = useElapsed(startedAt)

  const tableCount = bundle.contracts.reduce((n, c) => n + c.schema.length, 0)

  async function analyze() {
    setStartedAt(Date.now())
    setError('')
    setAppliedCount(0)
    try {
      const response = await api.enrich(bundle)
      setProposals(response.proposals)
      setWarnings(response.warnings ?? [])
      setDecisions(
        Object.fromEntries(response.proposals.map((p) => [p.id, { status: 'pending', value: p.value, edited: false }])),
      )
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setStartedAt(null)
    }
  }

  function decide(ids: string[], status: Status) {
    setDecisions((prev) => {
      const next = { ...prev }
      for (const id of ids) {
        if (next[id] && next[id].status !== 'applied') {
          next[id] = { ...next[id], status }
        }
      }
      return next
    })
  }

  function saveEdit(id: string, value: unknown) {
    setDecisions((prev) => ({ ...prev, [id]: { status: 'accepted', value, edited: true } }))
  }

  const accepted = (proposals ?? []).filter((p) => decisions[p.id]?.status === 'accepted')

  async function apply() {
    setApplying(true)
    setError('')
    try {
      const payload = accepted.map((p) => ({ ...p, value: decisions[p.id].value }))
      const next = await api.apply(bundle, payload)
      decide(payload.map((p) => p.id), 'applied')
      setAppliedCount((n) => n + payload.length)
      await onApplied(next)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setApplying(false)
    }
  }

  const counts = { pending: 0, accepted: 0, rejected: 0, applied: 0 }
  for (const d of Object.values(decisions)) counts[d.status] += 1

  // contractId → table → proposals, in the order Cortex returned them.
  const groups = new Map<string, Map<string, Proposal[]>>()
  for (const p of proposals ?? []) {
    const byTable = groups.get(p.contractId) ?? new Map<string, Proposal[]>()
    byTable.set(p.table, [...(byTable.get(p.table) ?? []), p])
    groups.set(p.contractId, byTable)
  }
  const contractName = (id: string) => bundle.contracts.find((c) => c.id === id)?.name ?? id

  return (
    <section className="step">
      <div className="card hero-card">
        <div>
          <h2>Analyze with Cortex</h2>
          <p className="muted">
            Snowflake Cortex reads each of the {tableCount} table{tableCount === 1 ? '' : 's'} and proposes descriptions,
            business names, classifications, tags, data-quality rules and SLAs (collectively named{' '}
            <a
              href="https://medium.com/data-mesh-learning/what-is-data-qos-and-why-is-it-critical-c524b81e3cc1"
              target="_blank"
              rel="noopener noreferrer"
            >
              Data QoS
            </a>
            ). Nothing reaches a contract until you accept it.
          </p>
        </div>
        <button type="button" className="btn-primary btn-large" disabled={startedAt !== null} onClick={() => void analyze()}>
          {startedAt !== null ? <Spinner label={`Analyzing… ${elapsed}s`} /> : proposals ? 'Re-analyze' : 'Analyze with Cortex'}
        </button>
      </div>

      {startedAt !== null && (
        <div className="progress-card">
          <div className="progress-bar">
            <span style={{ width: `${Math.min(95, (elapsed / Math.max(30, tableCount * 6)) * 100)}%` }} />
          </div>
          <p className="muted">
            One Cortex call per table · {elapsed}s elapsed · usually 30–90 s. Keep this tab open.
          </p>
        </div>
      )}

      <ErrorBanner error={error} onDismiss={() => setError('')} />
      {warnings.map((w, i) => (
        <div key={i} className="banner banner-warn">
          <strong>Warning</strong>
          <span className="banner-text">{w}</span>
        </div>
      ))}

      {proposals && proposals.length === 0 && <p className="muted">Cortex had no proposals for these tables.</p>}

      {proposals && proposals.length > 0 && (
        <div className="review-bar card">
          <span className="pill">{proposals.length} proposals</span>
          <span className="pill pill-ok">{counts.accepted} accepted</span>
          <span className="pill pill-fail">{counts.rejected} rejected</span>
          <span className="pill">{counts.pending} pending</span>
          {counts.applied > 0 && <span className="pill pill-accent">{counts.applied} applied</span>}
          <span className="spacer" />
          <button type="button" className="btn-primary" disabled={applying || accepted.length === 0} onClick={() => void apply()}>
            {applying ? <Spinner label="Applying…" /> : `Apply ${accepted.length} accepted to contracts`}
          </button>
        </div>
      )}

      {appliedCount > 0 && !applying && (
        <div className="banner banner-ok" role="status">
          <span className="badge badge-ok">✓ Applied</span>
          <span className="banner-text">
            {appliedCount} proposal{appliedCount === 1 ? '' : 's'} written into the contracts; YAML refreshed.
          </span>
          <button type="button" className="btn-ghost btn-small" onClick={onViewDocuments}>
            View documents →
          </button>
        </div>
      )}

      {[...groups].map(([contractId, byTable]) => (
        <div key={contractId} className="contract-group">
          <h2>
            <span className="tab-kind">ODCS</span> {contractName(contractId)}
          </h2>
          {[...byTable].map(([tableName, items]) => {
            const open = items.filter((p) => decisions[p.id]?.status !== 'applied').map((p) => p.id)
            return (
              <div key={tableName} className="table-group card">
                <div className="table-group-head">
                  <h3 className="mono">{tableName}</h3>
                  <span className="muted">{items.length} proposals</span>
                  <span className="spacer" />
                  <button type="button" className="btn-ok btn-small" disabled={open.length === 0} onClick={() => decide(open, 'accepted')}>
                    Accept all for this table
                  </button>
                  <button type="button" className="btn-danger btn-small" disabled={open.length === 0} onClick={() => decide(open, 'rejected')}>
                    Reject all
                  </button>
                </div>
                {sectionsOf(items).map((section) => {
                  const pending = section.items
                    .filter((p) => decisions[p.id]?.status !== 'applied')
                    .map((p) => p.id)
                  return (
                    <section key={section.kind} className="kind-section">
                      <div className="kind-section-head">
                        <h4>{KIND_LABEL[section.kind as ProposalKind] ?? section.kind}</h4>
                        <span className="muted">{section.items.length}</span>
                        <span className="spacer" />
                        <button
                          type="button"
                          className="btn-ok btn-small"
                          disabled={pending.length === 0}
                          onClick={() => decide(pending, 'accepted')}
                        >
                          Accept all
                        </button>
                        <button
                          type="button"
                          className="btn-danger btn-small"
                          disabled={pending.length === 0}
                          onClick={() => decide(pending, 'rejected')}
                        >
                          Reject all
                        </button>
                      </div>
                      <div className="proposal-grid">
                        {section.items.map((p) => (
                          <ProposalCard
                            key={p.id}
                            proposal={p}
                            decision={decisions[p.id]}
                            onAccept={() => decide([p.id], 'accepted')}
                            onReject={() => decide([p.id], 'rejected')}
                            onReset={() => decide([p.id], 'pending')}
                            onSaveEdit={(value) => saveEdit(p.id, value)}
                          />
                        ))}
                      </div>
                    </section>
                  )
                })}
              </div>
            )
          })}
        </div>
      ))}
    </section>
  )
}

function formatValue(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

interface CardProps {
  proposal: Proposal
  decision: Decision
  onAccept: () => void
  onReject: () => void
  onReset: () => void
  onSaveEdit: (value: unknown) => void
}

function ProposalCard({ proposal, decision, onAccept, onReject, onReset, onSaveEdit }: CardProps) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [draftError, setDraftError] = useState('')
  const status = decision?.status ?? 'pending'
  const value = decision?.value ?? proposal.value

  function startEdit() {
    setDraft(formatValue(value))
    setDraftError('')
    setEditing(true)
  }

  function commitEdit() {
    if (isText(proposal.kind)) {
      if (!draft.trim()) {
        setDraftError('Value cannot be empty.')
        return
      }
      onSaveEdit(draft.trim())
    } else {
      try {
        const parsed: unknown = JSON.parse(draft)
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          setDraftError('Expected a JSON object.')
          return
        }
        onSaveEdit(parsed)
      } catch (cause) {
        setDraftError(`Invalid JSON: ${messageOf(cause)}`)
        return
      }
    }
    setEditing(false)
  }

  return (
    <article className={`proposal is-${status}`}>
      <header className="proposal-head">
        <span className={`kind-badge kind-${proposal.kind}`}>{KIND_LABEL[proposal.kind] ?? proposal.kind}</span>
        <span className="mono proposal-target">{proposal.column ?? 'table'}</span>
        <span className="spacer" />
        {decision?.edited && <span className="pill">edited</span>}
        {status !== 'pending' && <span className={`status status-${status}`}>{status}</span>}
      </header>

      {editing ? (
        <div className="proposal-edit">
          {isText(proposal.kind) ? (
            <textarea rows={proposal.kind === 'description' ? 3 : 1} value={draft} onChange={(e) => setDraft(e.target.value)} autoFocus />
          ) : (
            <textarea rows={6} value={draft} onChange={(e) => setDraft(e.target.value)} autoFocus spellCheck={false} />
          )}
          {draftError && <p className="field-error">{draftError}</p>}
          <div className="actions">
            <button type="button" className="btn-ok btn-small" onClick={commitEdit}>
              Save &amp; accept
            </button>
            <button type="button" className="btn-ghost btn-small" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <ProposalValue kind={proposal.kind} value={value} />
      )}

      <p className="rationale">
        <span aria-hidden="true">✦</span> {proposal.rationale}
      </p>

      {!editing && status !== 'applied' && (
        <footer className="actions">
          {status === 'pending' ? (
            <>
              <button type="button" className="btn-ok btn-small" onClick={onAccept}>
                Accept
              </button>
              <button type="button" className="btn-secondary btn-small" onClick={startEdit}>
                Edit
              </button>
              <button type="button" className="btn-danger btn-small" onClick={onReject}>
                Reject
              </button>
            </>
          ) : (
            <>
              <button type="button" className="btn-ghost btn-small" onClick={onReset}>
                Undo
              </button>
              <button type="button" className="btn-secondary btn-small" onClick={startEdit}>
                Edit
              </button>
            </>
          )}
        </footer>
      )}
    </article>
  )
}

function ProposalValue({ kind, value }: { kind: ProposalKind; value: unknown }) {
  if (kind === 'qualityRule' && value && typeof value === 'object') {
    const rule = value as { type?: string; metric?: string; query?: string; mustBe?: unknown; description?: string }
    return (
      <div className="proposal-value">
        {rule.description && <strong>{rule.description}</strong>}
        {rule.query ? (
          <pre className="sql">{rule.query}</pre>
        ) : (
          <code>
            {rule.metric ?? rule.type}
          </code>
        )}
        {rule.mustBe !== undefined && <span className="muted"> must be {String(rule.mustBe)}</span>}
      </div>
    )
  }
  if (kind === 'sla' && value && typeof value === 'object') {
    const sla = value as { property?: string; value?: unknown; unit?: string; element?: string }
    return (
      <div className="proposal-value">
        <strong>{sla.property}</strong> = {String(sla.value)}
        {sla.unit ? ` ${sla.unit}` : ''}
        {sla.element && <span className="muted"> on {sla.element}</span>}
      </div>
    )
  }
  return <div className="proposal-value">{formatValue(value)}</div>
}
