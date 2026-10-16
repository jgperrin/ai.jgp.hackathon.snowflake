import { useState } from 'react'
import { MOCK, api } from './api'
import type { Bundle, BundleYaml, GenerateRequest } from './api'
import { DocumentsView } from './components/DocumentsView'
import { SourceStep } from './components/SourceStep'
import { ErrorBanner } from './components/common'
import { messageOf } from './lib/util'

const STEPS = [
  { key: 'source', title: 'Source', blurb: 'Pick a database, its schemas and tables' },
  { key: 'generate', title: 'Generate', blurb: 'ODPS product + one ODCS contract per schema' },
] as const

type StepKey = (typeof STEPS)[number]['key']

export default function App() {
  const [step, setStep] = useState<StepKey>('source')
  const [bundle, setBundle] = useState<Bundle | null>(null)
  const [yaml, setYaml] = useState<BundleYaml | null>(null)
  const [yamlError, setYamlError] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')

  /** Every change to the bundle goes through here so the YAML view never goes stale. */
  async function updateBundle(next: Bundle) {
    setBundle(next)
    setYaml(null)
    setYamlError('')
    try {
      setYaml(await api.toYaml(next))
    } catch (cause) {
      setYamlError(messageOf(cause))
    }
  }

  async function generate(request: GenerateRequest) {
    setGenerating(true)
    setError('')
    try {
      const generated = await api.generate(request)
      setStep('generate')
      await updateBundle(generated)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setGenerating(false)
    }
  }

  const reachable = (key: StepKey) => key === 'source' || bundle !== null

  return (
    <div className="app">
      {MOCK && <div className="mock-strip">Mock backend · illustrative sample data, not Snowflake</div>}
      <header className="masthead">
        <div className="brand">
          <span className="logo" aria-hidden="true">◆</span>
          <span className="brand-name">Contract Forge</span>
        </div>
        <h1>From Snowflake schema to data product</h1>
        <p className="standards">
          <span>ODCS v3.2</span>
          <span>ODPS v1.1</span>
          <span>OSI/Apache Ossie</span>
        </p>
      </header>

      <nav className="stepper" aria-label="Wizard steps">
        {STEPS.map((s, i) => (
          <button
            key={s.key}
            type="button"
            className={s.key === step ? 'stepper-item is-current' : 'stepper-item'}
            disabled={!reachable(s.key)}
            aria-current={s.key === step ? 'step' : undefined}
            onClick={() => setStep(s.key)}
          >
            <span className="stepper-num">{i + 1}</span>
            <span className="stepper-text">
              <span className="stepper-title">{s.title}</span>
              <span className="stepper-blurb">{s.blurb}</span>
            </span>
          </button>
        ))}
      </nav>

      <main>
        <ErrorBanner error={error} onDismiss={() => setError('')} />

        {/* Kept mounted (hidden) so going back preserves the selection. */}
        <div hidden={step !== 'source'}>
          <SourceStep busy={generating} onGenerate={(request) => void generate(request)} />
        </div>

        {step === 'generate' && bundle && (
          <section className="step">
            <DocumentsView bundle={bundle} yaml={yaml} yamlError={yamlError} />
          </section>
        )}
      </main>
    </div>
  )
}
