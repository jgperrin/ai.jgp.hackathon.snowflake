import { useEffect, useImperativeHandle, useState } from 'react'
import type { Ref } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
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

/** What the shell can do to a running wizard: load a saved bundle from the store view. */
export interface WizardHandle {
  load: (bundle: Bundle) => Promise<void>
}

interface Props {
  ref?: Ref<WizardHandle>
  /** Opens the full store view (step 5 links to it). */
  onBrowseStore: () => void
}

export function Wizard({ ref, onBrowseStore }: Props) {
  // The step is the first path segment (DIND#101): /analyze, /verify, and so on. Reading it from the
  // URL rather than state is what makes a step linkable, reloadable and reachable with Back.
  const navigate = useNavigate()
  const { pathname, state } = useLocation()
  const segment = pathname.split('/').filter(Boolean)[0]
  const step: StepKey = STEPS.find((s) => s.key === segment)?.key ?? 'source'
  const setStep = (key: StepKey) => void navigate(`/${key}`)
  // Set by the redirect below, carried in the history entry rather than in state, so the note
  // survives the replace without a second render.
  const needsSource = (state as { needsSource?: boolean } | null)?.needsSource === true

  const [bundle, setBundle] = useState<Bundle | null>(null)
  // Bumped on every new or loaded bundle, so per-bundle step state (proposals, DQ runs) starts fresh.
  const [generation, setGeneration] = useState(0)
  const [yaml, setYaml] = useState<BundleYaml | null>(null)
  const [yamlError, setYamlError] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  // Accepted but not yet applied, reported up by Analyze: Verify needs it to explain an empty run.
  const [pendingAccepted, setPendingAccepted] = useState(0)

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

  // Opening a save from the store view lands it exactly where step 5's Open does.
  useImperativeHandle(ref, () => ({ load: replaceBundle }))

  const reachable = (key: StepKey) => key === 'source' || bundle !== null
  const index = STEPS.findIndex((s) => s.key === step)
  const nextStep = STEPS[index + 1]

  // A step URL with no bundle in memory — a reload, or a link someone pasted — lands on Source and
  // says why, rather than showing an empty step (DIND#101). The bundle lives in memory only, so a
  // fresh load genuinely has nothing to show for /verify.
  useEffect(() => {
    if (bundle === null && step !== 'source') {
      void navigate('/source', { replace: true, state: { needsSource: true } })
    }
  }, [bundle, step, navigate])

  return (
    <>
      <nav className="stepper" aria-label="Wizard steps">
        {STEPS.map((s, i) => {
          const isCurrent = s.key === step
          const className = isCurrent ? 'stepper-item is-current' : 'stepper-item'
          const inner = (
            <>
              <span className="stepper-num">{i + 1}</span>
              <span className="stepper-text">
                <span className="stepper-title">{s.title}</span>
                <span className="stepper-blurb">{s.blurb}</span>
              </span>
            </>
          )
          // A reachable step is a real link, so it can be middle-clicked and opened in a new tab;
          // one that needs a product this browser does not have stays a disabled button.
          return reachable(s.key) ? (
            <Link key={s.key} className={className} to={`/${s.key}`} aria-current={isCurrent ? 'step' : undefined}>
              {inner}
            </Link>
          ) : (
            <button key={s.key} type="button" className={className} disabled>
              {inner}
            </button>
          )
        })}
      </nav>

      <div className="wizard-body">
        <ErrorBanner error={error} onDismiss={() => setError('')} />

        {needsSource && (
          <div className="banner banner-warn" role="status">
            <strong>Start from the source</strong>
            <span className="banner-text">
              That step needs a generated product, and this browser does not have one — a link or a reload starts
              fresh. Pick a database, its schemas and tables, then Generate.
            </span>
            <button
              type="button"
              className="btn-ghost btn-small"
              onClick={() => void navigate('/source', { replace: true, state: null })}
            >
              Dismiss
            </button>
          </div>
        )}

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
                onPending={setPendingAccepted}
              />
            </div>
            <div hidden={step !== 'verify'}>
              <VerifyStep key={generation} bundle={bundle} pendingAccepted={pendingAccepted} />
            </div>
            <div hidden={step !== 'publish'}>
              <PublishStep bundle={bundle} onLoad={replaceBundle} onBrowseStore={onBrowseStore} />
            </div>
          </>
        )}

        {bundle && step !== 'source' && nextStep && (
          <div className="actions next-step">
            <span className="spacer" />
            <Link className="btn-secondary btn-large" to={`/${nextStep.key}`}>
              Next: {nextStep.title} →
            </Link>
          </div>
        )}
      </div>
    </>
  )
}
