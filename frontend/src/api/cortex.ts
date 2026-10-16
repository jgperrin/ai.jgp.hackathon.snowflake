export interface CortexResponse {
  answer: string
}

/**
 * The one place that knows the backend's request and response shape.
 * Everything else in the UI works with plain strings.
 */
export async function complete(prompt: string): Promise<string> {
  const response = await fetch('/api/cortex/complete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt }),
  })

  if (!response.ok) {
    throw new Error(
      response.status === 400
        ? 'The prompt was rejected as empty.'
        : `Cortex call failed: ${response.status} ${response.statusText}`,
    )
  }

  const body = (await response.json()) as CortexResponse
  return body.answer
}
