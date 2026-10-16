/*
 * DEV-ONLY in-memory backend, loaded by src/api/index.ts when VITE_MOCK=1.
 * Everything here is illustrative sample data (FINSERV_DW); nothing comes from
 * Snowflake. It mimics the backend contract closely enough to demo every step.
 */
import type {
  Api,
  Bundle,
  Column,
  ContractDoc,
  ContractProperty,
  Database,
  Proposal,
  QualityResult,
  QualityRule,
  SavedProduct,
  Schema,
  SlaProperty,
  Table,
} from './types'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const clone = <T>(value: T): T => structuredClone(value)

type ColSpec = [name: string, dataType: string, nullable?: boolean, pk?: boolean, comment?: string]

function cols(specs: ColSpec[]): Column[] {
  return specs.map(([name, dataType, nullable = true, pk = false, comment = null], i) => ({
    name,
    dataType,
    nullable: pk ? false : nullable,
    comment: comment ?? null,
    ordinal: i + 1,
    primaryKey: pk,
  }))
}

function table(name: string, rowCount: number | null, comment: string | null, specs: ColSpec[], kind: 'TABLE' | 'VIEW' = 'TABLE'): Table {
  return { name, kind, rowCount, comment, columns: cols(specs) }
}

const DATABASES: Database[] = [
  { name: 'FINSERV_DW', comment: 'Financial services data warehouse (sample)' },
  { name: 'MARKETING_DB', comment: 'Campaigns and web analytics (sample)' },
]

const SCHEMAS: Record<string, Schema[]> = {
  FINSERV_DW: [
    { name: 'CORE', comment: 'Customers, accounts and branches' },
    { name: 'TRANSACTIONS', comment: 'Card and account movements' },
    { name: 'INSURANCE', comment: 'Policies and claims' },
    { name: 'RISK', comment: 'Credit scores and exposures' },
    { name: 'ANALYTICS', comment: 'Curated reporting views' },
  ],
  MARKETING_DB: [{ name: 'CAMPAIGNS', comment: 'Campaign performance' }],
}

const TABLES: Record<string, Table[]> = {
  'FINSERV_DW.CORE': [
    table('CUSTOMERS', 125_430, 'One row per retail customer', [
      ['CUSTOMER_ID', 'NUMBER(38,0)', false, true],
      ['FIRST_NAME', 'VARCHAR(100)', false],
      ['LAST_NAME', 'VARCHAR(100)', false],
      ['EMAIL', 'VARCHAR(255)'],
      ['PHONE', 'VARCHAR(32)'],
      ['DATE_OF_BIRTH', 'DATE'],
      ['SEGMENT', 'VARCHAR(20)', true, false, 'RETAIL, PREMIER or PRIVATE'],
      ['CREATED_AT', 'TIMESTAMP_NTZ(9)', false],
    ]),
    table('ACCOUNTS', 211_874, null, [
      ['ACCOUNT_ID', 'NUMBER(38,0)', false, true],
      ['CUSTOMER_ID', 'NUMBER(38,0)', false],
      ['ACCOUNT_TYPE', 'VARCHAR(20)', false],
      ['OPENED_ON', 'DATE', false],
      ['STATUS', 'VARCHAR(12)', false],
      ['BALANCE', 'NUMBER(18,2)'],
      ['CURRENCY', 'VARCHAR(3)', false],
    ]),
    table('BRANCHES', 342, 'Physical branches', [
      ['BRANCH_ID', 'NUMBER(38,0)', false, true],
      ['BRANCH_NAME', 'VARCHAR(120)', false],
      ['CITY', 'VARCHAR(80)'],
      ['STATE', 'VARCHAR(2)'],
    ]),
  ],
  'FINSERV_DW.TRANSACTIONS': [
    table('CARD_TRANSACTIONS', 8_942_117, null, [
      ['TXN_ID', 'VARCHAR(36)', false, true],
      ['ACCOUNT_ID', 'NUMBER(38,0)', false],
      ['TXN_TS', 'TIMESTAMP_NTZ(9)', false],
      ['AMOUNT', 'NUMBER(18,2)', false],
      ['CURRENCY', 'VARCHAR(3)', false],
      ['MERCHANT_NAME', 'VARCHAR(200)'],
      ['MCC', 'VARCHAR(4)'],
      ['IS_FRAUD', 'BOOLEAN'],
    ]),
    table('WIRE_TRANSFERS', 412_009, 'Outgoing and incoming wires', [
      ['WIRE_ID', 'VARCHAR(36)', false, true],
      ['ACCOUNT_ID', 'NUMBER(38,0)', false],
      ['DIRECTION', 'VARCHAR(3)', false],
      ['AMOUNT', 'NUMBER(18,2)', false],
      ['COUNTERPARTY_IBAN', 'VARCHAR(34)'],
      ['SENT_AT', 'TIMESTAMP_NTZ(9)'],
    ]),
  ],
  'FINSERV_DW.INSURANCE': [
    table('POLICIES', 58_211, null, [
      ['POLICY_ID', 'VARCHAR(20)', false, true],
      ['CUSTOMER_ID', 'NUMBER(38,0)', false],
      ['PRODUCT_LINE', 'VARCHAR(30)', false],
      ['PREMIUM', 'NUMBER(12,2)', false],
      ['START_DATE', 'DATE', false],
      ['END_DATE', 'DATE'],
    ]),
    table('CLAIMS', 14_876, 'Claims filed against policies', [
      ['CLAIM_ID', 'VARCHAR(20)', false, true],
      ['POLICY_ID', 'VARCHAR(20)', false],
      ['INCIDENT_DATE', 'DATE', false],
      ['REPORTED_DATE', 'DATE', false],
      ['CLAIM_AMOUNT', 'NUMBER(12,2)'],
      ['STATUS', 'VARCHAR(16)', false],
      ['ADJUSTER_NOTES', 'VARCHAR(4000)'],
    ]),
  ],
  'FINSERV_DW.RISK': [
    table('CREDIT_SCORES', 125_102, null, [
      ['CUSTOMER_ID', 'NUMBER(38,0)', false, true],
      ['SCORE', 'NUMBER(3,0)', false],
      ['BUREAU', 'VARCHAR(20)', false],
      ['SCORED_ON', 'DATE', false],
    ]),
    table('EXPOSURES', 98_440, 'Daily exposure per account', [
      ['ACCOUNT_ID', 'NUMBER(38,0)', false, true],
      ['AS_OF_DATE', 'DATE', false, true],
      ['EXPOSURE_AMOUNT', 'NUMBER(18,2)', false],
      ['PD', 'FLOAT', true, false, 'Probability of default'],
    ]),
  ],
  'FINSERV_DW.ANALYTICS': [
    table('V_CUSTOMER_360', null, 'Customer 360 reporting view', [
      ['CUSTOMER_ID', 'NUMBER(38,0)', false],
      ['TOTAL_BALANCE', 'NUMBER(18,2)'],
      ['ACTIVE_POLICIES', 'NUMBER(9,0)'],
      ['LAST_TXN_TS', 'TIMESTAMP_NTZ(9)'],
    ], 'VIEW'),
    table('V_DAILY_FRAUD', null, null, [
      ['TXN_DATE', 'DATE', false],
      ['FRAUD_COUNT', 'NUMBER(18,0)'],
      ['FRAUD_AMOUNT', 'NUMBER(18,2)'],
    ], 'VIEW'),
  ],
  'MARKETING_DB.CAMPAIGNS': [
    table('CAMPAIGN_RESULTS', 1_204, null, [
      ['CAMPAIGN_ID', 'NUMBER(38,0)', false, true],
      ['CLICKS', 'NUMBER(18,0)'],
    ]),
  ],
}

function logicalType(dataType: string): string {
  const t = dataType.toUpperCase()
  if (t.startsWith('NUMBER') || t.startsWith('FLOAT') || t.startsWith('INT')) return t.includes(',0)') ? 'integer' : 'number'
  if (t.startsWith('TIMESTAMP') || t === 'DATE') return 'date'
  if (t === 'BOOLEAN') return 'boolean'
  return 'string'
}

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-')

/** Mock validation: flag what the real validator would also flag (a contract with no schema objects). */
function validate(bundle: Bundle): Bundle {
  const errors = bundle.contracts
    .filter((c) => c.schema.length === 0)
    .map((c) => ({ document: c.id, path: '$.schema', message: 'must contain at least 1 item' }))
  return { ...bundle, validation: { valid: errors.length === 0, errors } }
}

// ---------- tiny YAML emitter (mock only; the real backend serializes) ----------

function scalar(value: unknown): string {
  if (value === null || value === undefined) return 'null'
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  const s = String(value)
  return /^[\w./ -]+$/.test(s) && !/^(true|false|null|yes|no|\d.*)$/i.test(s) && !s.includes(': ')
    ? s
    : JSON.stringify(s)
}

function toYaml(value: unknown, indent = ''): string {
  if (Array.isArray(value)) {
    if (value.length === 0) return ' []'
    return value
      .map((item) => {
        if (item && typeof item === 'object') {
          const body = toYaml(item, indent + '  ').replace(/^\n\s*/, '')
          return `\n${indent}- ${body}`
        }
        return `\n${indent}- ${scalar(item)}`
      })
      .join('')
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value).filter(([, v]) => v !== undefined)
    if (entries.length === 0) return ' {}'
    return entries
      .map(([k, v]) => {
        if (v && typeof v === 'object') return `\n${indent}${k}:${toYaml(v, indent + '  ')}`
        return `\n${indent}${k}: ${scalar(v)}`
      })
      .join('')
  }
  return ` ${scalar(value)}`
}

const yaml = (doc: unknown) => toYaml(doc).replace(/^\n/, '') + '\n'

// ---------- Cortex-like proposals ----------

let proposalSeq = 0
function proposal(contractId: string, tableName: string, column: string | null, kind: Proposal['kind'], value: unknown, rationale: string): Proposal {
  proposalSeq += 1
  return { id: `p-${proposalSeq}`, contractId, table: tableName, column, kind, value, rationale }
}

const KNOWN_DESCRIPTIONS: Record<string, string> = {
  CUSTOMER_ID: 'Surrogate key identifying a retail customer.',
  EMAIL: 'Primary e-mail address the customer gave at onboarding.',
  PHONE: 'Primary phone number, E.164 format when available.',
  INCIDENT_DATE: 'Date on which the insured incident occurred.',
  REPORTED_DATE: 'Date on which the claim was reported to the insurer.',
  CLAIM_AMOUNT: 'Amount claimed by the policy holder, in policy currency.',
  AMOUNT: 'Transaction amount in the transaction currency.',
  IS_FRAUD: 'True when the transaction was confirmed as fraudulent.',
  SCORE: 'Credit bureau score (300–850).',
}

const PII = new Set(['EMAIL', 'PHONE', 'DATE_OF_BIRTH', 'FIRST_NAME', 'LAST_NAME', 'COUNTERPARTY_IBAN'])

function proposalsFor(contract: ContractDoc): Proposal[] {
  const out: Proposal[] = []
  for (const object of contract.schema) {
    const t = object.physicalName ?? object.name
    if (!object.description) {
      out.push(proposal(contract.id, object.name, null, 'description', `Business records of ${object.name.toLowerCase().replace(/_/g, ' ')} as maintained by the source system.`, 'Table has no comment; inferred from its name and columns.'))
    }
    for (const p of object.properties) {
      if (!p.description && KNOWN_DESCRIPTIONS[p.name]) {
        out.push(proposal(contract.id, object.name, p.name, 'description', KNOWN_DESCRIPTIONS[p.name], 'Inferred from column name, type and sibling columns.'))
      }
      if (PII.has(p.name)) {
        out.push(proposal(contract.id, object.name, p.name, 'classification', 'restricted', `${p.name} identifies a natural person (PII); restrict access.`))
        out.push(proposal(contract.id, object.name, p.name, 'tag', 'pii', 'Personal data under GLBA/GDPR.'))
      }
      if (p.primaryKey) {
        out.push(proposal(contract.id, object.name, p.name, 'qualityRule', { type: 'library', metric: 'duplicateValues', mustBe: 0, description: `${p.name} is unique` }, 'Primary key must not contain duplicates.'))
      }
      if (p.name === 'EMAIL') {
        out.push(proposal(contract.id, object.name, p.name, 'businessName', 'Customer e-mail', 'Readable label for business users.'))
        out.push(proposal(contract.id, object.name, p.name, 'qualityRule', { type: 'library', metric: 'nullValues', mustBe: 0, description: 'Every customer has an e-mail' }, 'E-mail is used for statements; sample shows a few nulls worth surfacing.'))
      }
      if (p.name === 'AMOUNT' || p.name === 'CLAIM_AMOUNT') {
        out.push(proposal(contract.id, object.name, p.name, 'qualityRule', { type: 'sql', query: `SELECT COUNT(*) FROM ${t} WHERE ${p.name} < 0`, mustBe: 0, description: `${p.name} is never negative` }, 'Negative amounts indicate reversal rows leaking into the fact table.'))
      }
    }
    if (object.name === 'CLAIMS') {
      out.push(proposal(contract.id, object.name, 'REPORTED_DATE', 'qualityRule', { type: 'sql', query: `SELECT COUNT(*) FROM ${t} WHERE REPORTED_DATE < INCIDENT_DATE`, mustBe: 0, description: 'reported_date >= incident_date' }, 'A claim cannot be reported before the incident happened.'))
      out.push(proposal(contract.id, object.name, 'POLICY_ID', 'businessName', 'Policy number', 'Matches the label used on claim forms.'))
    }
  }
  const first = contract.schema[0]
  if (first) {
    out.push(proposal(contract.id, first.name, null, 'sla', { property: 'frequency', value: 1, unit: 'd', element: first.name }, 'Load history shows one batch per day.'))
    out.push(proposal(contract.id, first.name, null, 'sla', { property: 'latency', value: 4, unit: 'h' }, 'Batches land ~3 h after business close; 4 h leaves headroom.'))
  }
  return out
}

function applyOne(contract: ContractDoc, p: Proposal) {
  if (p.kind === 'sla') {
    contract.slaProperties = [...(contract.slaProperties ?? []), p.value as SlaProperty]
    return
  }
  const object = contract.schema.find((o) => o.name === p.table)
  if (!object) return
  const target: { description?: string; quality?: QualityRule[] } & Partial<ContractProperty> =
    p.column ? (object.properties.find((c) => c.name === p.column) ?? object) : object
  switch (p.kind) {
    case 'description':
      target.description = String(p.value)
      break
    case 'businessName':
      target.businessName = String(p.value)
      break
    case 'classification':
      target.classification = String(p.value)
      break
    case 'tag':
      target.tags = [...new Set([...(target.tags ?? []), String(p.value)])]
      break
    case 'qualityRule': {
      // SQL rules span columns: keep them on the schema object, like the backend does.
      const rule = p.value as QualityRule
      const holder = rule.type === 'sql' ? object : target
      holder.quality = [...(holder.quality ?? []), rule]
      break
    }
  }
}

const saved = new Map<string, { meta: SavedProduct; bundle: Bundle }>()

export const mockApi: Api = {
  async listDatabases() {
    await sleep(250)
    return clone(DATABASES)
  },
  async listSchemas(db) {
    await sleep(250)
    if (!SCHEMAS[db]) throw new Error(`Database ${db} does not exist or is not authorized.`)
    return clone(SCHEMAS[db])
  },
  async listTables(db, schema) {
    await sleep(300 + Math.random() * 400)
    return clone(TABLES[`${db}.${schema}`] ?? [])
  },
  async generate(req) {
    await sleep(900)
    const productId = slug(`${req.database}-product`)
    const contracts: ContractDoc[] = req.schemas.map(({ schema, tables }) => {
      const all = TABLES[`${req.database}.${schema}`] ?? []
      const comment = SCHEMAS[req.database]?.find((s) => s.name === schema)?.comment ?? undefined
      return {
        apiVersion: 'v3.2.0',
        kind: 'DataContract',
        id: slug(`${req.database}-${schema}`),
        name: schema.toLowerCase(),
        version: '1.0.0',
        status: 'draft',
        domain: req.database.toLowerCase(),
        description: { purpose: comment },
        servers: [{ server: 'snowflake', type: 'snowflake', database: req.database, schema }],
        schema: all
          .filter((t) => tables.includes(t.name))
          .map((t) => ({
            name: t.name,
            physicalName: `${req.database}.${schema}.${t.name}`,
            physicalType: t.kind === 'VIEW' ? 'view' : 'table',
            description: t.comment ?? undefined,
            properties: t.columns.map((c) => ({
              name: c.name,
              logicalType: logicalType(c.dataType),
              physicalType: c.dataType,
              required: !c.nullable,
              primaryKey: c.primaryKey,
              description: c.comment ?? undefined,
            })),
          })),
        slaProperties: [],
      }
    })
    const product = {
      apiVersion: 'v1.1.0',
      kind: 'DataProduct',
      id: productId,
      name: `${req.database} data product`,
      version: '1.0.0',
      status: 'draft',
      domain: req.database.toLowerCase(),
      outputPorts: contracts.map((c) => ({ name: c.name, version: c.version, contractId: c.id, type: 'tables' })),
    }
    return validate({ product, contracts, validation: { valid: true, errors: [] } })
  },
  async toYaml(bundle) {
    await sleep(200)
    return {
      product: yaml(bundle.product),
      contracts: Object.fromEntries(bundle.contracts.map((c) => [c.id, yaml(c)])),
    }
  },
  async enrich(bundle) {
    // One "AI call" per table, like the backend.
    const tables = bundle.contracts.reduce((n, c) => n + c.schema.length, 0)
    await sleep(Math.min(12_000, 1_200 + tables * 600))
    return {
      proposals: bundle.contracts.flatMap(proposalsFor),
      warnings: tables > 10 ? ['Mock: some tables were analyzed from a 100-row sample only.'] : [],
    }
  },
  async apply(bundle, accepted) {
    await sleep(500)
    const next = clone(bundle)
    for (const p of accepted) {
      const contract = next.contracts.find((c) => c.id === p.contractId)
      if (contract) applyOne(contract, p)
    }
    return validate(next)
  },
  async runQuality(bundle) {
    await sleep(1_500)
    const results: QualityResult[] = []
    for (const c of bundle.contracts) {
      for (const o of c.schema) {
        const fq = o.physicalName ?? o.name
        const rules: [string | null, QualityRule][] = [
          ...(o.quality ?? []).map((r): [string | null, QualityRule] => [null, r]),
          ...o.properties.flatMap((p) => (p.quality ?? []).map((r): [string | null, QualityRule] => [p.name, r])),
        ]
        for (const [column, rule] of rules) {
          const sql =
            rule.type === 'sql' && rule.query
              ? rule.query
              : rule.metric === 'duplicateValues'
                ? `SELECT COUNT(*) - COUNT(DISTINCT ${column}) FROM ${fq}`
                : `SELECT COUNT(*) FROM ${fq} WHERE ${column} IS NULL`
          // Deterministic sample outcomes: the claims date rule and e-mail nulls fail, the rest pass.
          const observed = rule.description === 'reported_date >= incident_date' ? 37 : column === 'EMAIL' && rule.metric === 'nullValues' ? 212 : 0
          results.push({
            contractId: c.id,
            table: o.name,
            column,
            rule: rule.description ?? rule.metric ?? 'rule',
            sql,
            passed: observed === (rule.mustBe ?? 0),
            observed,
            threshold: `= ${rule.mustBe ?? 0}`,
            error: null,
          })
        }
      }
    }
    const passed = results.filter((r) => r.passed).length
    return { results, passed, failed: results.length - passed }
  },
  async save(bundle) {
    await sleep(700)
    const prev = saved.get(bundle.product.id)
    const version = prev ? `1.0.${Number(prev.meta.version.split('.')[2]) + 1}` : '1.0.0'
    const meta = { id: bundle.product.id, name: bundle.product.name, version, savedAt: new Date().toISOString() }
    saved.set(meta.id, { meta, bundle: clone(bundle) })
    return { id: meta.id, version, savedAt: meta.savedAt }
  },
  async listProducts() {
    await sleep(200)
    return [...saved.values()].map((s) => s.meta)
  },
  async getProduct(id) {
    await sleep(200)
    const hit = saved.get(id)
    if (!hit) throw new Error(`No saved product ${id}`)
    return clone(hit.bundle)
  },
  async exportOsi(bundle) {
    await sleep(600)
    const doc = {
      version: '0.1',
      semantic_model: [
        {
          name: bundle.product.id,
          description: bundle.product.name,
          datasets: bundle.contracts.flatMap((c) =>
            c.schema.map((o) => ({
              name: o.name.toLowerCase(),
              source: o.physicalName,
              description: o.description,
              fields: o.properties.map((p) => ({ name: p.name.toLowerCase(), expression: p.name, description: p.description, label: p.businessName })),
            })),
          ),
        },
      ],
    }
    return { yaml: yaml(doc), valid: true, errors: [] }
  },
}
