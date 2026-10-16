import { useState } from 'react'
import { complete } from './api/cortex'

export default function App() {
  const [prompt, setPrompt] = useState('')
  const [answer, setAnswer] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!prompt.trim() || busy) {
      return
    }
    setBusy(true)
    setError('')
    setAnswer('')
    try {
      setAnswer(await complete(prompt))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <header>
        <h1>Snowflake Cortex</h1>
        <p className="subtitle">
          Spring Boot backend, React front end, Cortex doing the thinking.
        </p>
      </header>

      <form onSubmit={onSubmit}>
        <label htmlFor="prompt">Prompt</label>
        <textarea
          id="prompt"
          rows={4}
          value={prompt}
          placeholder="Explain Snowflake Cortex to a Java developer."
          onChange={(event) => setPrompt(event.target.value)}
        />
        <button type="submit" disabled={busy || !prompt.trim()}>
          {busy ? 'Asking Cortex…' : 'Ask Cortex'}
        </button>
      </form>

      {error && (
        <section className="panel panel-error" role="alert">
          {error}
        </section>
      )}

      {answer && (
        <section className="panel">
          <h2>Answer</h2>
          <p>{answer}</p>
        </section>
      )}
    </main>
  )
}
