import { useState } from 'react'
import actianLogo from '../assets/actian-footer-logo.svg'
import hclSoftwareLogo from '../assets/hcl-software-logo.svg'

/*
 * The Actian Labs family footer (ops/ux footer-spec.md, footer-snippet.html), ported from the
 * Schema Drift Advisor's copy of the Workbench reference. The social SVG paths and hrefs and the
 * six legal links are verbatim from footer-snippet.html; the two literals "DO NOT USE FOR PRODUCTION"
 * and "Use at your own risk." are never gated.
 *
 * Context Builder has no /health version endpoint, so the Server / Core Library slots are omitted
 * (footer-spec "Apps without a /health version endpoint"); the heart reflects whether the backend
 * answered the session check the shell already makes, so no extra request is sent.
 */

const LEGAL = [
  ['Terms of Use', 'https://www.actian.com/terms-of-use/'],
  ['Modern Slavery Policy', 'https://www.actian.com/modern-slavery-policy/'],
  ['Privacy Policy', 'https://www.actian.com/privacy-policy/'],
  ['Trademark Guidelines', 'https://www.actian.com/trademark-guidelines/'],
  ['Patents', 'https://www.actian.com/patents/'],
  ['Security', 'https://www.actian.com/company/trust-center/'],
] as const

const FOOTER_COLLAPSED_KEY = 'cb-footer-collapsed'

/** Fixed at load: the copyright year does not need to tick over mid-session. */
const YEAR = new Date().getFullYear()

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(FOOTER_COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}

export function Footer({ healthy }: { healthy: boolean }) {
  const [collapsed, setCollapsed] = useState(readCollapsed)

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev
      try {
        localStorage.setItem(FOOTER_COLLAPSED_KEY, String(next))
      } catch {
        // Storage can be blocked (private mode); the toggle still works for this page view.
      }
      return next
    })
  }

  if (collapsed) {
    return (
      <button
        type="button"
        className="footer-restore-btn"
        onClick={toggleCollapsed}
        title="Show footer"
        aria-label="Show footer"
      >
        <svg viewBox="0 0 24 24" fill="none" strokeWidth={2} stroke="currentColor" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 15.75l7.5-7.5 7.5 7.5" />
        </svg>
      </button>
    )
  }

  return (
    <footer className="footer">
      <button
        type="button"
        className="footer-collapse-btn"
        onClick={toggleCollapsed}
        title="Hide footer"
        aria-label="Hide footer"
      >
        <svg viewBox="0 0 24 24" fill="none" strokeWidth={2} stroke="currentColor" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
        </svg>
      </button>

      <div className="footer-top">
        <div className="footer-inner footer-main">
          <div>
            <a href="https://www.actian.com" target="_blank" rel="noopener noreferrer">
              <img src={actianLogo} alt="Actian" className="footer-actian" />
            </a>
            <p className="footer-division">a division of HCLSoftware</p>
            <p className="footer-desc">
              AI-Ready Data Product Experimentation Platform · Context Builder v{__APP_VERSION__} ·{' '}
              <span
                role="img"
                aria-label={healthy ? 'Backend reachable' : 'Backend unreachable'}
                title={healthy ? 'Backend reachable' : 'Backend unreachable'}
              >
                {healthy ? '💚' : '🧨'}
              </span>{' '}
              · DO NOT USE FOR PRODUCTION
            </p>
            <p className="footer-copy">
              © {YEAR} Actian Corporation. All rights reserved. Prototype made with 💜 by the{' '}
              <a href="https://www.actian.com/company/leadership-team/" target="_blank" rel="noopener noreferrer">
                Actian Office of the CTO
              </a>
              . Use at your own risk.
            </p>
          </div>

          <nav className="footer-social" aria-label="Actian on social media">
            <a href="https://www.linkedin.com/company/actian-corporation" target="_blank" rel="noopener noreferrer" aria-label="LinkedIn">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg>
            </a>
            <a href="https://www.youtube.com/user/ActianCorporation" target="_blank" rel="noopener noreferrer" aria-label="YouTube">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M23.498 6.186a3.016 3.016 0 00-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 00.502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 002.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 002.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/></svg>
            </a>
            <a href="https://github.com/ActianCorp" target="_blank" rel="noopener noreferrer" aria-label="GitHub">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/></svg>
            </a>
            <a href="https://x.com/actiancorp" target="_blank" rel="noopener noreferrer" aria-label="X">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>
            </a>
          </nav>
        </div>
      </div>

      <div className="footer-bottom">
        <div className="footer-inner footer-bottom-row">
          <nav className="footer-legal" aria-label="Legal">
            {LEGAL.map(([label, href]) => (
              <a key={label} href={href} target="_blank" rel="noopener noreferrer">
                {label}
              </a>
            ))}
          </nav>
          <a href="https://www.hcl-software.com" target="_blank" rel="noopener noreferrer">
            <img src={hclSoftwareLogo} alt="HCLSoftware" className="footer-hcl" />
          </a>
        </div>
      </div>
    </footer>
  )
}
