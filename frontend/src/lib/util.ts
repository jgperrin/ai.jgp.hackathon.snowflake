/** Turns anything thrown into a message worth showing. */
export function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause)
}

export function downloadText(filename: string, text: string, type = 'application/yaml') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function formatCount(n: number | null): string {
  if (n === null) {
    return '—'
  }
  return new Intl.NumberFormat('en-US', {
    notation: n >= 100_000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(n)
}
