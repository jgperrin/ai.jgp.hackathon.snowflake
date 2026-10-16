/** Raised for any non-2xx answer; the message is what the backend said, when it said something. */
export class ApiError extends Error {
  readonly status: number
  /** Machine-readable reason, when the backend sends one (e.g. "NO_CONNECTION" on a 409). */
  readonly code: string | null

  constructor(status: number, message: string, code: string | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export const NO_CONNECTION = 'NO_CONNECTION'
