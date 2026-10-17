import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  BETWEEN,
  DIMENSIONS,
  METRICS,
  OPERATORS,
  OPERATOR_LABEL,
  SEVERITIES,
  SLA_PROPERTIES,
  ruleFormOf,
  ruleOf,
  slaFormOf,
  slaOf,
} from '../lib/ruleForm'
import type { RuleForm, SlaForm } from '../lib/ruleForm'
import { MAX_CONSTRAINTS, MAX_STATEMENTS, contextFormOf, contextOf } from '../lib/contextForm'
import type { ContextForm } from '../lib/contextForm'

/**
 * The editor a DQ rule, an SLA or an RFC-0038 context block opens in (DIND#104, DIND#109). The form
 * models live in `lib/ruleForm` and `lib/contextForm`; this file is only the markup.
 */

interface Props {
  kind: 'qualityRule' | 'sla' | 'context'
  value: unknown
  onSave: (value: unknown) => void
  onCancel: () => void
  /** Hand the value to the raw JSON editor instead. */
  onRawJson: () => void
}

/** The editor a DQ rule, an SLA or a context block opens in. */
export function RuleEditor({ kind, value, onSave, onCancel, onRawJson }: Props) {
  if (kind === 'context') {
    return <ContextEditor value={value} onSave={onSave} onCancel={onCancel} onRawJson={onRawJson} />
  }
  return kind === 'sla' ? (
    <SlaEditor value={value} onSave={onSave} onCancel={onCancel} onRawJson={onRawJson} />
  ) : (
    <QualityRuleEditor value={value} onSave={onSave} onCancel={onCancel} onRawJson={onRawJson} />
  )
}

function Footer({ error, onCancel, onRawJson }: { error: string; onCancel: () => void; onRawJson: () => void }) {
  return (
    <>
      {error && <p className="field-error">{error}</p>}
      <div className="actions">
        <button type="submit" className="btn-ok btn-small">
          Save &amp; accept
        </button>
        <button type="button" className="btn-ghost btn-small" onClick={onCancel}>
          Cancel
        </button>
        <button type="button" className="btn-ghost btn-small" onClick={onRawJson}>
          Edit as JSON
        </button>
      </div>
    </>
  )
}

function QualityRuleEditor({ value, onSave, onCancel, onRawJson }: Omit<Props, 'kind'>) {
  const [form, setForm] = useState<RuleForm>(() => ruleFormOf(value))
  const [error, setError] = useState('')
  const set = <K extends keyof RuleForm>(key: K, next: RuleForm[K]) => setForm((prev) => ({ ...prev, [key]: next }))

  function submit(event: FormEvent) {
    event.preventDefault()
    const built = ruleOf(form)
    if ('error' in built) {
      setError(built.error)
      return
    }
    onSave(built.rule)
  }

  return (
    <form className="rule-form" onSubmit={submit}>
      <label className="form-field">
        <span className="field-label">Kind of rule</span>
        <select value={form.type} onChange={(e) => set('type', e.target.value as RuleForm['type'])}>
          <option value="library">Library metric, compiled to SQL for you</option>
          <option value="sql">SQL, written here and run as one SELECT</option>
        </select>
      </label>

      {form.type === 'sql' ? (
        <label className="form-field">
          <span className="field-label">
            Query
            <span className="hint">
              Counts violations. <code>{'${table}'}</code> and <code>{'${column}'}</code> are filled in; one SELECT
              only.
            </span>
          </span>
          <textarea rows={4} spellCheck={false} value={form.query} onChange={(e) => set('query', e.target.value)} />
        </label>
      ) : (
        <>
          <label className="form-field">
            <span className="field-label">Metric</span>
            <select value={form.metric} onChange={(e) => set('metric', e.target.value)}>
              {METRICS.map((metric) => (
                <option key={metric} value={metric}>
                  {metric}
                </option>
              ))}
            </select>
          </label>
          {form.metric === 'invalidValues' && (
            <>
              <label className="form-field">
                <span className="field-label">
                  Valid values
                  <span className="hint">Comma separated. Leave empty to use a pattern instead.</span>
                </span>
                <input
                  type="text"
                  spellCheck={false}
                  value={form.validValues}
                  onChange={(e) => set('validValues', e.target.value)}
                />
              </label>
              <label className="form-field">
                <span className="field-label">
                  Pattern
                  <span className="hint">A regular expression, used when no list of values is given.</span>
                </span>
                <input
                  type="text"
                  spellCheck={false}
                  value={form.pattern}
                  onChange={(e) => set('pattern', e.target.value)}
                />
              </label>
            </>
          )}
        </>
      )}

      <label className="form-field">
        <span className="field-label">The measured number</span>
        <select value={form.operator} onChange={(e) => set('operator', e.target.value)}>
          {OPERATORS.map((operator) => (
            <option key={operator} value={operator}>
              {OPERATOR_LABEL[operator] ?? operator}
            </option>
          ))}
        </select>
      </label>

      {BETWEEN.has(form.operator) ? (
        <div className="rule-row">
          <label className="form-field">
            <span className="field-label">Low</span>
            <input type="text" inputMode="decimal" value={form.low} onChange={(e) => set('low', e.target.value)} />
          </label>
          <label className="form-field">
            <span className="field-label">High</span>
            <input type="text" inputMode="decimal" value={form.high} onChange={(e) => set('high', e.target.value)} />
          </label>
        </div>
      ) : (
        <label className="form-field">
          <span className="field-label">Threshold</span>
          <input
            type="text"
            inputMode="decimal"
            value={form.threshold}
            onChange={(e) => set('threshold', e.target.value)}
          />
        </label>
      )}

      <label className="form-field">
        <span className="field-label">Description</span>
        <input type="text" value={form.description} onChange={(e) => set('description', e.target.value)} />
      </label>

      <div className="rule-row">
        <label className="form-field">
          <span className="field-label">Dimension</span>
          <select value={form.dimension} onChange={(e) => set('dimension', e.target.value)}>
            <option value="">—</option>
            {DIMENSIONS.map((dimension) => (
              <option key={dimension} value={dimension}>
                {dimension}
              </option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span className="field-label">Severity</span>
          <select value={form.severity} onChange={(e) => set('severity', e.target.value)}>
            <option value="">—</option>
            {SEVERITIES.map((severity) => (
              <option key={severity} value={severity}>
                {severity}
              </option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span className="field-label">Unit</span>
          <input type="text" value={form.unit} onChange={(e) => set('unit', e.target.value)} />
        </label>
      </div>

      <Footer error={error} onCancel={onCancel} onRawJson={onRawJson} />
    </form>
  )
}

function SlaEditor({ value, onSave, onCancel, onRawJson }: Omit<Props, 'kind'>) {  const [form, setForm] = useState<SlaForm>(() => slaFormOf(value))
  const [error, setError] = useState('')
  const set = <K extends keyof SlaForm>(key: K, next: SlaForm[K]) => setForm((prev) => ({ ...prev, [key]: next }))

  function submit(event: FormEvent) {
    event.preventDefault()
    const built = slaOf(form)
    if ('error' in built) {
      setError(built.error)
      return
    }
    onSave(built.sla)
  }

  return (
    <form className="rule-form" onSubmit={submit}>
      <label className="form-field">
        <span className="field-label">Property</span>
        <select value={form.property} onChange={(e) => set('property', e.target.value)}>
          {SLA_PROPERTIES.map((property) => (
            <option key={property} value={property}>
              {property}
            </option>
          ))}
        </select>
      </label>
      <label className="form-field">
        <span className="field-label">
          Value
          <span className="hint">A number where the property is a duration, otherwise text.</span>
        </span>
        <input type="text" value={form.value} onChange={(e) => set('value', e.target.value)} />
      </label>
      <div className="rule-row">
        <label className="form-field">
          <span className="field-label">Unit</span>
          <input type="text" value={form.unit} onChange={(e) => set('unit', e.target.value)} />
        </label>
        <label className="form-field">
          <span className="field-label">Element</span>
          <input type="text" value={form.element} onChange={(e) => set('element', e.target.value)} />
        </label>
      </div>
      <label className="form-field">
        <span className="field-label">Description</span>
        <input type="text" value={form.description} onChange={(e) => set('description', e.target.value)} />
      </label>
      <Footer error={error} onCancel={onCancel} onRawJson={onRawJson} />
    </form>
  )
}

function ContextEditor({ value, onSave, onCancel, onRawJson }: Omit<Props, 'kind'>) {
  const [form, setForm] = useState<ContextForm>(() => contextFormOf(value))
  const [error, setError] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    const built = contextOf(form)
    if ('error' in built) {
      setError(built.error)
      return
    }
    onSave(built.context)
  }

  const setStatement = (index: number, key: 'question' | 'answer', text: string) =>
    setForm((prev) => ({
      ...prev,
      statements: prev.statements.map((s, i) => (i === index ? { ...s, [key]: text } : s)),
    }))

  const setConstraint = (index: number, text: string) =>
    setForm((prev) => ({
      ...prev,
      constraints: prev.constraints.map((c, i) => (i === index ? { constraint: text } : c)),
    }))

  return (
    <form className="rule-form" onSubmit={submit}>
      <label className="form-field">
        <span className="field-label">
          Instructions
          <span className="hint">
            How an AI agent should use this table. It travels to the contract as a scoped system prompt.
          </span>
        </span>
        <textarea
          rows={3}
          value={form.instructions}
          onChange={(e) => setForm((prev) => ({ ...prev, instructions: e.target.value }))}
        />
      </label>

      <div className="context-list">
        <span className="field-label">
          Questions this table answers
          <span className="hint">
            Up to {MAX_STATEMENTS}. Give an answer only where the profile actually supports one.
          </span>
        </span>
        {form.statements.map((statement, i) => (
          <div key={i} className="context-row">
            <input
              type="text"
              placeholder="Canonical question"
              value={statement.question}
              onChange={(e) => setStatement(i, 'question', e.target.value)}
            />
            <input
              type="text"
              placeholder="Answer, optional"
              value={statement.answer}
              onChange={(e) => setStatement(i, 'answer', e.target.value)}
            />
            <button
              type="button"
              className="btn-ghost btn-small"
              onClick={() => setForm((prev) => ({ ...prev, statements: prev.statements.filter((_, j) => j !== i) }))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="btn-secondary btn-small"
          disabled={form.statements.length >= MAX_STATEMENTS}
          onClick={() =>
            setForm((prev) => ({ ...prev, statements: [...prev.statements, { question: '', answer: '' }] }))
          }
        >
          Add a question
        </button>
      </div>

      <div className="context-list">
        <span className="field-label">
          What it must not do
          <span className="hint">
            Up to {MAX_CONSTRAINTS}. Negative guidance: personal data to leave alone, totals that must not be summed,
            joins that mean nothing.
          </span>
        </span>
        {form.constraints.map((constraint, i) => (
          <div key={i} className="context-row">
            <input
              type="text"
              placeholder="Constraint"
              value={constraint.constraint}
              onChange={(e) => setConstraint(i, e.target.value)}
            />
            <button
              type="button"
              className="btn-ghost btn-small"
              onClick={() => setForm((prev) => ({ ...prev, constraints: prev.constraints.filter((_, j) => j !== i) }))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="btn-secondary btn-small"
          disabled={form.constraints.length >= MAX_CONSTRAINTS}
          onClick={() => setForm((prev) => ({ ...prev, constraints: [...prev.constraints, { constraint: '' }] }))}
        >
          Add a constraint
        </button>
      </div>

      <Footer error={error} onCancel={onCancel} onRawJson={onRawJson} />
    </form>
  )
}
