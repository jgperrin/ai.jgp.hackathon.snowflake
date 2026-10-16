import { useState } from 'react'
import { api } from './api'
import type { Bundle, BundleYaml, GenerateRequest } from './api'
import { AnalyzeStep } from './components/AnalyzeStep'
import { DocumentsView } from './components/DocumentsView'
import { PublishStep } from './components/PublishStep'
import { SourceStep } from './components/SourceStep'
import { VerifyStep } from './components/VerifyStep'
import { ErrorBanner } from './components/common'
import { messageOf } from './lib/util'

const STEPS = [
  { key: 'source', title: 'Source', blurb: 'Pick a database, its schemas and tables' },
  { key: 'generate', title: 'Generate', blurb: 'ODPS product + one ODCS contract per schema' },
  { key: 'analyze', title: 'Analyze', blurb: 'Cortex proposes, you accept, edit or reject' },
  { key: 'verify', title: 'Verify', blurb: 'Run the data-quality rules on Snowflake' },
  { key: 'publish', title: 'Publish', blurb: 'Save to Snowflake, export OSI/Apache Ossie' },
] as const

type StepKey = (typeof STEPS)[number]['key']

export function Wizard() {
  const [step, setStep] = useState<StepKey>('source')
  const [bundle, setBundle] = useState<Bundle | null>(null)
  // Bumped on every new or loaded bundle, so per-bundle step state (proposals, DQ runs) starts fresh.
  const [generation, setGeneration] = useState(0)
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

  async function replaceBundle(next: Bundle) {
    setGeneration((g) => g + 1)
    setStep('generate')
    await updateBundle(next)
  }

  async function generate(request: GenerateRequest) {
    setGenerating(true)
    setError('')
    try {
      await replaceBundle(await api.generate(request))
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setGenerating(false)
    }
  }

  const reachable = (key: StepKey) => key === 'source' || bundle !== null
  const index = STEPS.findIndex((s) => s.key === step)
  const nextStep = STEPS[index + 1]

  return (
    <>
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

        {/* Steps stay mounted (hidden) so moving around the wizard keeps selections, proposals and results. */}
        <div hidden={step !== 'source'}>
          <SourceStep busy={generating} onGenerate={(request) => void generate(request)} />
        </div>

        {bundle && (
          <>
            <div hidden={step !== 'generate'}>
              <section className="step">
                <DocumentsView bundle={bundle} yaml={yaml} yamlError={yamlError} />
              </section>
            </div>
            <div hidden={step !== 'analyze'}>
              <AnalyzeStep
                key={generation}
                bundle={bundle}
                onApplied={updateBundle}
                onViewDocuments={() => setStep('generate')}
              />
            </div>
            <div hidden={step !== 'verify'}>
              <VerifyStep key={generation} bundle={bundle} />
            </div>
            <div hidden={step !== 'publish'}>
              <PublishStep bundle={bundle} onLoad={replaceBundle} />
            </div>
          </>
        )}

        {bundle && step !== 'source' && nextStep && (
          <div className="actions next-step">
            <span className="spacer" />
            <button type="button" className="btn-secondary btn-large" onClick={() => setStep(nextStep.key)}>
              Next: {nextStep.title} →
            </button>
          </div>
        )}
      </main>
    </>
  )
}
