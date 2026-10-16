const REPO = 'https://github.com/jgperrin/ai.jgp.hackathon.snowflake'

interface Props {
  onBack: () => void
}

/**
 * About Context Builder (DIND#82). The poster is the content area's background (set on
 * `.content.is-about` by App, from the bundled asset so it survives the /contextbuilder/ subpath);
 * this is only the copy, on a palette surface so it stays legible over the artwork.
 */
export function AboutView({ onBack }: Props) {
  return (
    <article className="card about-card" aria-labelledby="about-title">
      <h1 id="about-title">About Context Builder</h1>
      <p className="about-lede">
        Everybody wants to build a context layer for AI, but what does it look like, and how do you build and
        maintain it? Context Builder analyzes your Snowflake data and builds that layer with open standards.
      </p>
      <p>
        Pick a database, tick the schemas and tables, and it generates one data contract per schema, bundled as the
        output ports of a data product. It profiles the data with bounded SQL, asks Snowflake Cortex to propose the
        semantic layer (descriptions, business names, PII classification, tags, data-quality rules and SLAs), and
        keeps a human in the loop: nothing reaches a contract until you accept or edit it.
      </p>
      <p className="about-steps">
        <strong>Source</strong> → <strong>Generate</strong> → <strong>Analyze</strong> → <strong>Verify</strong> →{' '}
        <strong>Publish</strong>
      </p>
      <p>
        It writes <strong>Bitol ODCS v3.2</strong> contracts and a <strong>Bitol ODPS v1.1</strong> product, and
        derives an <strong>OSI/Apache Ossie</strong> semantic model from them.
      </p>
      <p>
        Source code:{' '}
        <a href={REPO} target="_blank" rel="noreferrer">
          github.com/jgperrin/ai.jgp.hackathon.snowflake
        </a>
      </p>
      <p className="muted about-disclaimer">
        Prototype made by the Actian Office of the CTO for the Data in the D hackathon. Do not use for production;
        use at your own risk.
      </p>
      <div>
        <button type="button" className="btn-secondary" onClick={onBack}>
          ← Back
        </button>
      </div>
    </article>
  )
}
