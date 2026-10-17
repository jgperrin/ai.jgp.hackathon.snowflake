/**
 * The form model behind the RFC-0038 context editor (DIND#109): instructions for an AI agent, the
 * canonical questions it can answer, and what it must not do.
 *
 * The caps match the backend's `ContextBlocks`: it keeps the first four statements and the first
 * three constraints, so the form stops at the same numbers rather than letting a fifth be typed and
 * silently dropped.
 */

export const MAX_STATEMENTS = 4
export const MAX_CONSTRAINTS = 3

export interface ContextForm {
  instructions: string
  statements: { question: string; answer: string }[]
  constraints: { constraint: string }[]
}

const str = (v: unknown) => (typeof v === 'string' ? v : v === undefined || v === null ? '' : String(v))

export function contextFormOf(value: unknown): ContextForm {
  const block = (value ?? {}) as Record<string, unknown>
  const statements = Array.isArray(block.verifiedStatements) ? block.verifiedStatements : []
  const constraints = Array.isArray(block.constraints) ? block.constraints : []
  return {
    instructions: str(block.instructions),
    statements: statements
      .filter((s): s is Record<string, unknown> => typeof s === 'object' && s !== null)
      .map((s) => ({ question: str(s.question), answer: str(s.answer) })),
    constraints: constraints
      .filter((c): c is Record<string, unknown> => typeof c === 'object' && c !== null)
      .map((c) => ({ constraint: str(c.constraint) })),
  }
}

/** The block the backend will accept, or the reason it cannot be sent. */
export function contextOf(form: ContextForm): { context: Record<string, unknown> } | { error: string } {
  const statements = form.statements
    .map((s) => ({ question: s.question.trim(), answer: s.answer.trim() }))
    .filter((s) => s.question !== '')
    .map((s) => (s.answer === '' ? { question: s.question } : { question: s.question, answer: s.answer }))
  const constraints = form.constraints
    .map((c) => c.constraint.trim())
    .filter((c) => c !== '')
    .map((constraint) => ({ constraint }))

  const instructions = form.instructions.trim()
  if (instructions === '' && statements.length === 0 && constraints.length === 0) {
    return { error: 'A context block needs instructions, a question, or a constraint.' }
  }

  const block: Record<string, unknown> = {}
  if (instructions !== '') {
    block.instructions = instructions
  }
  if (statements.length > 0) {
    block.verifiedStatements = statements
  }
  if (constraints.length > 0) {
    block.constraints = constraints
  }
  return { context: block }
}
