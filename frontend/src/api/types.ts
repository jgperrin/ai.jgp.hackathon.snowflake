/*
 * Backend shapes. src/api/ is the only place that knows them; the UI imports
 * these types but never builds a URL or parses a response itself.
 */

export interface Database {
  name: string
  comment: string | null
}

export interface Schema {
  name: string
  comment: string | null
}

export interface Column {
  name: string
  dataType: string
  nullable: boolean
  comment: string | null
  ordinal: number
  primaryKey: boolean
}

export interface Table {
  name: string
  kind: 'TABLE' | 'VIEW'
  rowCount: number | null
  comment: string | null
  columns: Column[]
}

export interface SchemaSelection {
  schema: string
  tables: string[]
}

export interface GenerateRequest {
  database: string
  schemas: SchemaSelection[]
}

export interface OutputPort {
  name: string
  version: string
  contractId: string
  [key: string]: unknown
}

export interface ProductDoc {
  id: string
  name: string
  outputPorts: OutputPort[]
  [key: string]: unknown
}

export interface QualityRule {
  type?: string
  metric?: string
  query?: string
  mustBe?: number
  description?: string
  [key: string]: unknown
}

export interface SlaProperty {
  property: string
  value: unknown
  unit?: string
  element?: string
}

export interface ContractProperty {
  name: string
  logicalType?: string
  physicalType?: string
  required?: boolean
  primaryKey?: boolean
  description?: string
  businessName?: string
  classification?: string
  tags?: string[]
  quality?: QualityRule[]
  [key: string]: unknown
}

export interface ContractSchemaObject {
  name: string
  physicalName?: string
  description?: string
  properties: ContractProperty[]
  quality?: QualityRule[]
  [key: string]: unknown
}

export interface ContractDoc {
  id: string
  name: string
  version: string
  schema: ContractSchemaObject[]
  slaProperties?: SlaProperty[]
  [key: string]: unknown
}

export interface ValidationError {
  document: string
  path: string
  message: string
}

export interface Validation {
  valid: boolean
  errors: ValidationError[]
}

export interface Bundle {
  product: ProductDoc
  contracts: ContractDoc[]
  validation: Validation
}

export interface BundleYaml {
  product: string
  contracts: Record<string, string>
}

export type ProposalKind =
  | 'description'
  | 'businessName'
  | 'classification'
  | 'tag'
  | 'qualityRule'
  | 'sla'

export interface Proposal {
  id: string
  contractId: string
  table: string
  column: string | null
  kind: ProposalKind
  value: unknown
  rationale: string
}

export interface EnrichResponse {
  proposals: Proposal[]
  warnings: string[]
}

export interface QualityResult {
  contractId: string
  table: string
  column: string | null
  rule: string
  sql: string
  passed: boolean
  observed: number | null
  threshold: string
  error: string | null
}

export interface QualityRun {
  results: QualityResult[]
  passed: number
  failed: number
}

export interface SaveResult {
  id: string
  version: string
  savedAt: string
}

export interface SavedProduct {
  id: string
  name: string
  version: string
  savedAt: string
}

export interface OsiExport {
  yaml: string
  valid: boolean
  errors: string[]
}

/** Everything the UI can ask the backend; implemented over HTTP and by the dev mock. */
export interface Api {
  listDatabases(): Promise<Database[]>
  listSchemas(database: string): Promise<Schema[]>
  listTables(database: string, schema: string): Promise<Table[]>
  generate(request: GenerateRequest): Promise<Bundle>
  toYaml(bundle: Bundle): Promise<BundleYaml>
  enrich(bundle: Bundle): Promise<EnrichResponse>
  apply(bundle: Bundle, accepted: Proposal[]): Promise<Bundle>
  runQuality(bundle: Bundle): Promise<QualityRun>
  save(bundle: Bundle): Promise<SaveResult>
  listProducts(): Promise<SavedProduct[]>
  getProduct(id: string): Promise<Bundle>
  exportOsi(bundle: Bundle): Promise<OsiExport>
}
