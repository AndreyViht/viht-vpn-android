import React, { useEffect, useRef } from 'react';

// A delegated listener follows the pointer without React rerenders.
export function useGlassPointer() {
  const ref = useRef(null);
  useEffect(() => {
    const root = ref.current;
    if (!root || !window.matchMedia('(hover: hover)').matches) return;
    let frame = 0;
    let hovered = null;
    let x = 0;
    let y = 0;
    const move = (event) => {
      hovered = event.target.closest('button, .fluent-card, .fluent-server-row, .fluent-theme-card');
      x = event.clientX;
      y = event.clientY;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        if (!hovered || !root.contains(hovered)) return;
        const rect = hovered.getBoundingClientRect();
        hovered.style.setProperty('--glass-x', `${x - rect.left}px`);
        hovered.style.setProperty('--glass-y', `${y - rect.top}px`);
      });
    };
    root.addEventListener('pointermove', move, { passive: true });
    return () => {
      root.removeEventListener('pointermove', move);
      cancelAnimationFrame(frame);
    };
  }, []);
  return ref;
}

function LiquidBackdrop() {
  return (
    <div className="liquid-backdrop" aria-hidden="true">
      <div className="liquid-orb liquid-orb--mint" />
      <div className="liquid-orb liquid-orb--blue" />
      <div className="liquid-orb liquid-orb--violet" />
      <div className="liquid-backdrop-grain" />
    </div>
  );
}

export default React.memo(LiquidBackdrop);
