/**
 * The form model behind the DQ rule and SLA editor (DIND#104): what a rule or an SLA looks like as
 * fields, and how fields become the object the backend accepts.
 *
 * What the form can emit is exactly what the backend accepts. `QualityRules.sanitize` and
 * `RuleCompiler` are the reference for every rule here, and `QualityRulesTest` pins each shape this
 * produces, so the two cannot drift apart silently.
 */

export const METRICS = ['rowCount', 'nullValues', 'missingValues', 'duplicateValues', 'invalidValues']
export const OPERATORS = [
  'mustBe',
  'mustNotBe',
  'mustBeGreaterThan',
  'mustBeGreaterOrEqualTo',
  'mustBeLessThan',
  'mustBeLessOrEqualTo',
  'mustBeBetween',
  'mustNotBeBetween',
]
export const DIMENSIONS = [
  'accuracy',
  'completeness',
  'conformity',
  'consistency',
  'coverage',
  'timeliness',
  'uniqueness',
]
export const SEVERITIES = ['error', 'warning']
export const SLA_PROPERTIES = [
  'freshness',
  'latency',
  'retention',
  'frequency',
  'availability',
  'throughput',
  'errorRate',
  'generalAvailability',
  'endOfSupport',
  'endOfLife',
  'timeOfAvailability',
]

/** The two operators that take a pair of bounds rather than one value. */
export const BETWEEN = new Set(['mustBeBetween', 'mustNotBeBetween'])

export const OPERATOR_LABEL: Record<string, string> = {
  mustBe: 'is exactly',
  mustNotBe: 'is not',
  mustBeGreaterThan: 'is greater than',
  mustBeGreaterOrEqualTo: 'is at least',
  mustBeLessThan: 'is less than',
  mustBeLessOrEqualTo: 'is at most',
  mustBeBetween: 'is between',
  mustNotBeBetween: 'is not between',
}

/** Mirrors QualityRules.isReadOnlySelect in the backend: one statement, read-only, no second one. */
const WRITE_KEYWORD =
  /\b(INSERT|UPDATE|DELETE|MERGE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE|CALL|PUT|COPY|EXECUTE|USE)\b/i

export function isReadOnlySelect(query: string): boolean {
  let q = query.trim()
  if (q.endsWith(';')) {
    q = q.slice(0, -1)
  }
  const upper = q.toUpperCase()
  return (upper.startsWith('SELECT') || upper.startsWith('WITH')) && !q.includes(';') && !WRITE_KEYWORD.test(q)
}

export interface RuleForm {
  type: 'library' | 'sql'
  metric: string
  query: string
  operator: string
  threshold: string
  low: string
  high: string
  validValues: string
  pattern: string
  unit: string
  dimension: string
  severity: string
  description: string
}

export interface SlaForm {
  property: string
  value: string
  unit: string
  element: string
  description: string
}

const str = (v: unknown) => (typeof v === 'string' ? v : v === undefined || v === null ? '' : String(v))

/** Reads a stored rule into the form, so editing starts from what is there. */
export function ruleFormOf(value: unknown): RuleForm {
  const rule = (value ?? {}) as Record<string, unknown>
  const args = (rule.arguments ?? {}) as Record<string, unknown>
  const operator = OPERATORS.find((op) => rule[op] !== undefined) ?? 'mustBe'
  const bounds = BETWEEN.has(operator) && Array.isArray(rule[operator]) ? (rule[operator] as unknown[]) : []
  return {
    type: rule.type === 'sql' ? 'sql' : 'library',
    metric: str(rule.metric) || 'nullValues',
    query: str(rule.query),
    operator,
    threshold: BETWEEN.has(operator) ? '' : str(rule[operator]),
    low: str(bounds[0]),
    high: str(bounds[1]),
    validValues: Array.isArray(args.validValues) ? args.validValues.map(str).join(', ') : '',
    pattern: str(args.pattern),
    unit: str(rule.unit),
    dimension: str(rule.dimension),
    severity: str(rule.severity),
    description: str(rule.description),
  }
}

export function slaFormOf(value: unknown): SlaForm {
  const sla = (value ?? {}) as Record<string, unknown>
  return {
    property: str(sla.property) || 'freshness',
    value: str(sla.value),
    unit: str(sla.unit),
    element: str(sla.element),
    description: str(sla.description),
  }
}

const number = (text: string): number | null => {
  const trimmed = text.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isFinite(n) ? n : null
}

/** The rule object the backend will accept, or the reason it cannot be sent. */
export function ruleOf(form: RuleForm): { rule: Record<string, unknown> } | { error: string } {
  const rule: Record<string, unknown> = { type: form.type }
  if (form.type === 'sql') {
    if (!isReadOnlySelect(form.query)) {
      return {
        error: 'A SQL rule must be one read-only SELECT (or WITH), with no second statement and no write keyword.',
      }
    }
    rule.query = form.query.trim().replace(/;$/, '')
  } else {
    rule.metric = form.metric
    const validValues = form.validValues
      .split(',')
      .map((v) => v.trim())
      .filter((v) => v !== '')
    const args: Record<string, unknown> = {}
    if (validValues.length > 0) {
      args.validValues = validValues
    }
    if (form.pattern.trim() !== '') {
      args.pattern = form.pattern.trim()
    }
    if (Object.keys(args).length > 0) {
      rule.arguments = args
    }
    if (form.metric === 'invalidValues' && !args.validValues && !args.pattern) {
      return { error: 'invalidValues needs either a list of valid values or a pattern.' }
    }
  }

  if (BETWEEN.has(form.operator)) {
    const low = number(form.low)
    const high = number(form.high)
    if (low === null || high === null) {
      return { error: 'Both bounds must be numbers.' }
    }
    if (low >= high) {
      return { error: 'The low bound must be below the high bound.' }
    }
    rule[form.operator] = [low, high]
  } else {
    const value = number(form.threshold)
    if (value === null) {
      return { error: 'This operator needs a number.' }
    }
    rule[form.operator] = value
  }

  for (const [key, text] of [
    ['description', form.description],
    ['dimension', form.dimension],
    ['severity', form.severity],
    ['unit', form.unit],
  ] as const) {
    if (text.trim() !== '') {
      rule[key] = text.trim()
    }
  }
  return { rule }
}

export function slaOf(form: SlaForm): { sla: Record<string, unknown> } | { error: string } {
  if (form.value.trim() === '') {
    return { error: 'An SLA needs a value.' }
  }
  const sla: Record<string, unknown> = { property: form.property }
  const asNumber = number(form.value)
  sla.value = asNumber === null ? form.value.trim() : asNumber
  for (const [key, text] of [
    ['unit', form.unit],
    ['element', form.element],
    ['description', form.description],
  ] as const) {
    if (text.trim() !== '') {
      sla[key] = text.trim()
    }
  }
  return { sla }
}
