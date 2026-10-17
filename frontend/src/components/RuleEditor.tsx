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

/**
 * The editor a DQ rule or an SLA opens in (DIND#104). The form model lives in `lib/ruleForm`; this
 * file is only the markup.
 */

interface Props {
  kind: 'qualityRule' | 'sla'
  value: unknown
  onSave: (value: unknown) => void
  onCancel: () => void
  /** Hand the value to the raw JSON editor instead. */
  onRawJson: () => void
}

/** The editor a DQ rule or an SLA opens in. */
export function RuleEditor({ kind, value, onSave, onCancel, onRawJson }: Props) {
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

function SlaEditor({ value, onSave, onCancel, onRawJson }: Omit<Props, 'kind'>) {
  const [form, setForm] = useState<SlaForm>(() => slaFormOf(value))
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
