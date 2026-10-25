/**
 * Platform & Device Capability Detector
 */
export const isAndroid = typeof window !== 'undefined' && (
  !!window.VihtNative ||
  !!window.AndroidBridge ||
  /Android/i.test(navigator.userAgent)
);

export const isMobile = typeof window !== 'undefined' && (
  isAndroid ||
  /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) ||
  (!window.electronAPI && typeof window.innerWidth === 'number' && window.innerWidth < 680)
);
