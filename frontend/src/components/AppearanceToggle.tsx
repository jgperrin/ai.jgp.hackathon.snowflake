import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { APPEARANCE_KEY, applyAppearance, readAppearance, saveAppearance } from '../lib/appearance'
import type { AppearanceMode } from '../lib/appearance'

/*
 * The System / Light / Dark switch in the top bar (DIND#59): three icon buttons, monochrome inline
 * SVG in currentColor (the app carries no icon dependency). The active one is aria-pressed.
 */

const ICON_PROPS = {
  width: 16,
  height: 16,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
} as const

const MODES: readonly { mode: AppearanceMode; label: string; icon: ReactNode }[] = [
  {
    mode: 'system',
    label: 'Match system',
    icon: (
      <svg {...ICON_PROPS}>
        <rect x="2" y="3" width="20" height="14" rx="2" />
        <path d="M8 21h8M12 17v4" />
      </svg>
    ),
  },
  {
    mode: 'light',
    label: 'Light',
    icon: (
      <svg {...ICON_PROPS}>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
      </svg>
    ),
  },
  {
    mode: 'dark',
    label: 'Dark',
    icon: (
      <svg {...ICON_PROPS}>
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>
    ),
  },
]

export function AppearanceToggle() {
  const [mode, setMode] = useState<AppearanceMode>(readAppearance)

  // Another tab changed the choice: follow it, so every open tab shows the same appearance.
  useEffect(() => {
    function onStorage(event: StorageEvent) {
      if (event.key === APPEARANCE_KEY || event.key === null) {
        const next = readAppearance()
        applyAppearance(next)
        setMode(next)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  function choose(next: AppearanceMode) {
    saveAppearance(next)
    setMode(next)
  }

  return (
    <div className="appearance-toggle" role="group" aria-label="Appearance">
      {MODES.map(({ mode: m, label, icon }) => (
        <button
          key={m}
          type="button"
          className="appearance-btn"
          aria-pressed={mode === m}
          aria-label={label}
          title={label}
          onClick={() => choose(m)}
        >
          {icon}
        </button>
      ))}
    </div>
  )
}
