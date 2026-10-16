/*
 * Appearance mode (DIND#59): System (default) follows prefers-color-scheme; Light and Dark are an
 * explicit per-browser choice. The mode lands on <html> as data-theme ("light" | "dark", absent for
 * System), which index.css keys its dark tokens on, plus an inline color-scheme so native
 * scrollbars, selects and textareas match.
 *
 * index.html repeats APPEARANCE_KEY and the apply logic in an inline script so first paint is
 * already right; keep the two in step.
 */

export type AppearanceMode = 'system' | 'light' | 'dark'

export const APPEARANCE_KEY = 'cb-appearance'

function isMode(value: unknown): value is AppearanceMode {
  return value === 'system' || value === 'light' || value === 'dark'
}

/** The stored choice; System when nothing (or nothing valid) is stored, or storage is blocked. */
export function readAppearance(): AppearanceMode {
  try {
    const stored = localStorage.getItem(APPEARANCE_KEY)
    return isMode(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}

/** Puts the mode on the document. System clears both, so the CSS media query decides again. */
export function applyAppearance(mode: AppearanceMode) {
  const root = document.documentElement
  if (mode === 'system') {
    delete root.dataset.theme
    root.style.colorScheme = ''
  } else {
    root.dataset.theme = mode
    root.style.colorScheme = mode
  }
}

/** Remembers the mode for this browser (System is stored as no entry) and applies it. */
export function saveAppearance(mode: AppearanceMode) {
  try {
    if (mode === 'system') {
      localStorage.removeItem(APPEARANCE_KEY)
    } else {
      localStorage.setItem(APPEARANCE_KEY, mode)
    }
  } catch {
    // Storage can be blocked (private mode); the choice still applies for this page view.
  }
  applyAppearance(mode)
}
