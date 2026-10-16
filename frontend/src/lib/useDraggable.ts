import { useRef, useState } from 'react'
import type { PointerEvent, RefObject } from 'react'

interface Drag {
  px: number
  py: number
  ox: number
  oy: number
  rect: DOMRect
  bounds: DOMRect
}

/**
 * Drag `target` by a handle, as a translation from where layout put it (DIND#91). The move is
 * clamped to the closest `.content` area, so the window can never be dropped out of reach.
 * When `enabled` is false (phone width) the offset reads as zero and the handle does nothing.
 */
export function useDraggable(target: RefObject<HTMLElement | null>, enabled: boolean) {
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  const drag = useRef<Drag | null>(null)

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    const el = target.current
    const area = el?.closest('.content')
    if (!enabled || event.button !== 0 || !el || !area) return
    event.preventDefault() // no text selection, no scroll
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = {
      px: event.clientX,
      py: event.clientY,
      ox: offset.x,
      oy: offset.y,
      rect: el.getBoundingClientRect(),
      bounds: area.getBoundingClientRect(),
    }
    setDragging(true)
  }

  function onPointerMove(event: PointerEvent<HTMLElement>) {
    const d = drag.current
    if (!d) return
    // Clamp against the rect at drag start, so the whole window stays inside the content area.
    const dx = Math.min(Math.max(event.clientX - d.px, d.bounds.left - d.rect.left), d.bounds.right - d.rect.right)
    const dy = Math.min(Math.max(event.clientY - d.py, d.bounds.top - d.rect.top), d.bounds.bottom - d.rect.bottom)
    setOffset({ x: d.ox + dx, y: d.oy + dy })
  }

  function onPointerEnd(event: PointerEvent<HTMLElement>) {
    if (!drag.current) return
    drag.current = null
    setDragging(false)
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
  }

  return {
    offset: enabled ? offset : { x: 0, y: 0 },
    dragging,
    handleProps: { onPointerDown, onPointerMove, onPointerUp: onPointerEnd, onPointerCancel: onPointerEnd },
  }
}
