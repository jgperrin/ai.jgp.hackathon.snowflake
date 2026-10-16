import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'

/** Where the signed-in user's Snowflake connection stands; null while it is being checked. */
export type ConnectionStatus = 'connected' | 'not-connected' | 'needs-setup' | null

const STATUS_LABEL: Record<Exclude<ConnectionStatus, null>, string> = {
  // Saved or open (DIND#67): the pool opens on first use, so 'ready' rather than 'connected'.
  connected: 'Ready',
  'not-connected': 'Not connected',
  'needs-setup': 'Needs setup',
}

interface Props {
  /** The signed-in user's name, shown beside the avatar. */
  username: string
  status: ConnectionStatus
  /** Opens the existing Snowflake connection panel. */
  onOpenConnection: () => void
  onSignOut: () => void
}

/** Up to two letters for the avatar: the first letters of the name's parts, else its first two letters. */
function initials(name: string): string {
  const parts = name.split(/[^\p{L}\p{N}]+/u).filter(Boolean)
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : (parts[0] ?? name).slice(0, 2)
  return letters.toUpperCase() || '?'
}

/**
 * The profile menu at the right of the top bar (DIND#69, ops/ux shell: avatar plus name). The avatar
 * carries the connection state as a dot so the bar keeps the glance; the menu holds the Snowflake
 * connection (which opens the existing panel) and Sign out. WAI-ARIA menu button pattern: arrows move,
 * Home/End jump, Escape or Tab closes, Escape returns focus to the trigger, a click outside closes.
 */
export function ProfileMenu({ username, status, onOpenConnection, onSignOut }: Props) {
  const [open, setOpen] = useState(false)
  // Which item takes focus when the menu opens: the first, or the last when opened with ArrowUp.
  const [initialFocus, setInitialFocus] = useState<'first' | 'last'>('first')
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLUListElement>(null)

  const statusLabel = status ? STATUS_LABEL[status] : 'Checking…'
  const dotClass = status === 'connected' ? 'profile-dot profile-dot-ok' : 'profile-dot profile-dot-warn'

  function items(): HTMLButtonElement[] {
    return Array.from(menu.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])
  }

  // On open, move focus into the menu.
  useEffect(() => {
    if (!open) return
    const all = items()
    ;(initialFocus === 'last' ? all[all.length - 1] : all[0])?.focus()
  }, [open, initialFocus])

  // A press anywhere outside the trigger and the menu closes it, without stealing the click.
  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (root.current && !root.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function openMenu(focus: 'first' | 'last') {
    setInitialFocus(focus)
    setOpen(true)
  }

  function closeMenu(restoreFocus: boolean) {
    setOpen(false)
    if (restoreFocus) trigger.current?.focus()
  }

  function onTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      openMenu(event.key === 'ArrowUp' ? 'last' : 'first')
    }
    // Enter and Space reach onClick as a native button press.
  }

  function onMenuKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    const all = items()
    const at = all.indexOf(document.activeElement as HTMLButtonElement)
    let next: number | null = null
    switch (event.key) {
      case 'ArrowDown':
        next = (at + 1) % all.length
        break
      case 'ArrowUp':
        next = (at - 1 + all.length) % all.length
        break
      case 'Home':
        next = 0
        break
      case 'End':
        next = all.length - 1
        break
      case 'Escape':
        event.preventDefault()
        closeMenu(true)
        return
      case 'Tab':
        // Let focus move on naturally; the menu just stops being open.
        setOpen(false)
        return
      default:
        return
    }
    event.preventDefault()
    all[next]?.focus()
  }

  function choose(action: () => void) {
    // Focus goes back to the trigger first, so the modal connection panel returns it there on close;
    // sign-out unmounts this menu anyway.
    closeMenu(true)
    action()
  }

  return (
    <div className="profile-menu" ref={root}>
      <button
        ref={trigger}
        type="button"
        className="profile-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="profile-menu-list"
        aria-label={`Account: ${username}, Snowflake ${statusLabel.toLowerCase()}`}
        title={`Snowflake: ${statusLabel}`}
        onClick={() => (open ? closeMenu(false) : openMenu('first'))}
        onKeyDown={onTriggerKeyDown}
      >
        <span className="profile-avatar" aria-hidden="true">
          {initials(username)}
          <span className={dotClass} />
        </span>
        <span className="profile-name">{username}</span>
        <svg viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 4.5 6 7.5 9 4.5" />
        </svg>
      </button>
      {open && (
        <ul
          ref={menu}
          id="profile-menu-list"
          className="profile-popover"
          role="menu"
          aria-label="Account"
          onKeyDown={onMenuKeyDown}
        >
          <li role="none">
            <button type="button" role="menuitem" tabIndex={-1} className="profile-item" onClick={() => choose(onOpenConnection)}>
              <span>Snowflake connection</span>
              <span className={status === 'connected' ? 'badge badge-ok' : 'badge pill-warn'}>{statusLabel}</span>
            </button>
          </li>
          <li role="separator" className="profile-separator" />
          <li role="none">
            <button type="button" role="menuitem" tabIndex={-1} className="profile-item" onClick={() => choose(onSignOut)}>
              Sign out
            </button>
          </li>
        </ul>
      )}
    </div>
  )
}
