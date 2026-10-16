import { useRef, useState } from 'react'
import { HelpPanel } from './HelpPanel'

interface Props {
  /** The signed-in user, sent with the report. */
  user: string
  /** Whether a Snowflake connection works; sent as a boolean only. */
  connected: boolean | null
}

/** The `?` in the top bar (ops/ux shell): opens the Help panel, and takes focus back when it closes. */
export function HelpButton({ user, connected }: Props) {
  const [open, setOpen] = useState(false)
  const button = useRef<HTMLButtonElement>(null)

  function close() {
    setOpen(false)
    // After the dialog has gone: while it is modal, the rest of the page is inert and cannot take focus.
    requestAnimationFrame(() => button.current?.focus())
  }

  return (
    <>
      <button
        ref={button}
        type="button"
        className="btn-ghost help-button"
        aria-label="Help: report a problem"
        title="Help: report a problem"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <svg viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
          <circle cx="10" cy="10" r="8" />
          <path strokeLinecap="round" d="M7.8 7.6a2.3 2.3 0 1 1 3.3 2.1c-.7.3-1.1.9-1.1 1.6v.4" />
          <circle cx="10" cy="14.4" r="0.6" fill="currentColor" stroke="none" />
        </svg>
      </button>
      {open && <HelpPanel user={user} connected={connected} onClose={close} />}
    </>
  )
}
