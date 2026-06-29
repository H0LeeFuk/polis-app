import { useEffect, useRef } from "react";

// Makes a popup window draggable by its header. Attach the returned ref to the window element
// (the centered card, NOT the backdrop). The header is auto-detected by class — dragging anywhere
// on it moves the window, except over interactive controls (buttons, links, inputs, selects).
// The window is kept inside the viewport so its header can never be dragged out of reach.
const HEADER_SELECTOR = ".modal-header, .mvov-head, .inv2-head, .profile-head";

export function useDraggable<T extends HTMLElement = HTMLDivElement>() {
  const ref = useRef<T>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const handle = (el.querySelector(HEADER_SELECTOR) as HTMLElement) ?? el;

    let dragging = false;
    let startX = 0, startY = 0, originX = 0, originY = 0, tx = 0, ty = 0;

    const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

    const onDown = (e: PointerEvent) => {
      if (e.button !== 0) return;
      if ((e.target as HTMLElement).closest("button, a, input, select, textarea")) return;
      dragging = true;
      startX = e.clientX; startY = e.clientY;
      originX = tx; originY = ty;
      handle.setPointerCapture(e.pointerId);
      handle.style.cursor = "grabbing";
      e.preventDefault();
    };
    const onMove = (e: PointerEvent) => {
      if (!dragging) return;
      let nx = originX + (e.clientX - startX);
      let ny = originY + (e.clientY - startY);
      // base (untranslated) position, so the whole window stays inside the viewport
      const r = el.getBoundingClientRect();
      const baseLeft = r.left - tx, baseTop = r.top - ty;
      nx = clamp(nx, -baseLeft, window.innerWidth - r.width - baseLeft);
      ny = clamp(ny, -baseTop, window.innerHeight - r.height - baseTop);
      tx = nx; ty = ny;
      el.style.transform = `translate(${tx}px, ${ty}px)`;
    };
    const onUp = () => { dragging = false; handle.style.cursor = "grab"; };

    handle.style.cursor = "grab";
    handle.style.touchAction = "none";
    handle.addEventListener("pointerdown", onDown);
    handle.addEventListener("pointermove", onMove);
    handle.addEventListener("pointerup", onUp);
    handle.addEventListener("pointercancel", onUp);
    return () => {
      handle.removeEventListener("pointerdown", onDown);
      handle.removeEventListener("pointermove", onMove);
      handle.removeEventListener("pointerup", onUp);
      handle.removeEventListener("pointercancel", onUp);
    };
  }, []);

  return ref;
}
