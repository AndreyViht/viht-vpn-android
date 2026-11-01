export function newsPopoverLayout(anchor, viewport) {
  const gap = 10, margin = Math.min(12, viewport.width / 8);
  const width = Math.max(1, Math.min(420, viewport.width - margin * 2));
  const leftEdge = viewport.left || 0, topEdge = viewport.top || 0;
  const left = Math.max(leftEdge + margin, Math.min(anchor.right - width, leftEdge + viewport.width - margin - width));
  const top = Math.max(topEdge + margin, Math.min(anchor.bottom + gap, topEdge + viewport.height - margin - 1));
  return { left, top, width, maxHeight: Math.max(1, Math.min(560, topEdge + viewport.height - margin - top)),
    transformOrigin: `${Math.max(12, Math.min(width - 12, (anchor.left + anchor.right) / 2 - left))}px 0px` };
}
