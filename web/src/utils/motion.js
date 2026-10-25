/**
 * VihtVPN Motion Design System
 * Inspired by Dynamic Island physics & Windows 11 Fluent Acrylic aesthetics:
 * - Spring-driven momentum (mass, stiffness, damping)
 * - Shared-surface morphing & smooth dimensional changes
 * - Micro-interactions for buttons, toggles and cards
 * - Zero jank, hardware-accelerated transforms & opacity
 */

export const springs = {
  // Ultra-responsive, tactile feel for buttons & toggles
  tactile: {
    type: 'spring',
    stiffness: 420,
    damping: 24,
    mass: 0.65,
  },
  // Smooth organic transitions for cards, surfaces and hero block
  natural: {
    type: 'spring',
    stiffness: 270,
    damping: 27,
    mass: 0.9,
  },
  // Soft, cushioned movement for dialogs and toasts
  cushioned: {
    type: 'spring',
    stiffness: 300,
    damping: 26,
    mass: 0.9,
  },
  // Snappy layout transitions for sliding active indicator
  pill: {
    type: 'spring',
    stiffness: 310,
    damping: 27,
    mass: 0.85,
  },
};

export const pageVariants = {
  initial: {
    opacity: 0,
    y: 14,
    scale: 0.98,
    filter: 'blur(5px)',
  },
  animate: {
    opacity: 1,
    y: 0,
    scale: 1,
    filter: 'blur(0px)',
    transition: springs.natural,
  },
  exit: {
    opacity: 0,
    y: -10,
    scale: 0.98,
    filter: 'blur(4px)',
    transition: {
      duration: 0.14,
      ease: [0.32, 0, 0.67, 0],
    },
  },
};

export const heroVariants = {
  disconnected: {
    scale: 1,
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.45)',
    transition: springs.natural,
  },
  connecting: {
    scale: 1.01,
    boxShadow: '0 12px 42px rgba(99, 102, 241, 0.25)',
    transition: springs.natural,
  },
  connected: {
    scale: 1,
    boxShadow: '0 12px 40px rgba(0, 255, 136, 0.22)',
    transition: springs.natural,
  },
  error: {
    scale: 0.99,
    boxShadow: '0 8px 32px rgba(239, 68, 68, 0.25)',
    transition: springs.tactile,
  },
};

export const buttonPress = {
  whileHover: { scale: 1.02, y: -1, transition: springs.tactile },
  whileTap: { scale: 0.96, transition: springs.tactile },
};

export const cardPress = {
  whileHover: { y: -2, transition: springs.tactile },
  whileTap: { scale: 0.985, transition: springs.tactile },
};
