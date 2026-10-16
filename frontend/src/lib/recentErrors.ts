/**
 * The last few API failures, kept in memory for the Help panel so a report says what went
 * wrong without a screenshot. Only status, call and message: never a request body, a header
 * or a cookie, so no credential can end up here.
 */
export type RecentError = {
  at: string
  status: number
  call: string
  message: string
}

const CAPACITY = 10
const MAX_MESSAGE = 300
const buffer: RecentError[] = []

export function recordError(status: number, call: string, message: string) {
  buffer.push({ at: new Date().toISOString(), status, call, message: message.slice(0, MAX_MESSAGE) })
  if (buffer.length > CAPACITY) {
    buffer.shift()
  }
}

/** Oldest first, a copy. */
export function recentErrors(): RecentError[] {
  return buffer.slice()
}
