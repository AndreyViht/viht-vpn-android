import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Check, Zap, AlertCircle, Info, X } from 'lucide-react';
import { springs } from '../utils/motion';

export default function IosNotificationBanner({ toast, onClose }) {
  return (
    <div className="fluent-toast-container" aria-live="polite">
      <AnimatePresence mode="wait">
        {toast && (
          <motion.div
            key={toast.id || toast.message}
            className={`fluent-toast-pill fluent-toast-pill--${toast.type || 'success'}`}
            initial={{ opacity: 0, y: -24, scale: 0.92 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -16, scale: 0.94, transition: { duration: 0.18 } }}
            transition={springs.cushioned}
            layout
          >
            <div className={`fluent-toast-icon-wrap fluent-toast-icon-wrap--${toast.type || 'success'}`}>
              {toast.type === 'ping' ? (
                <Zap size={14} className="fluent-toast-icon" />
              ) : toast.type === 'error' ? (
                <AlertCircle size={14} className="fluent-toast-icon" />
              ) : toast.type === 'info' ? (
                <Info size={14} className="fluent-toast-icon" />
              ) : (
                <Check size={14} className="fluent-toast-icon" />
              )}
            </div>

            <div className="fluent-toast-body">
              <span className="fluent-toast-message">{toast.message}</span>
            </div>

            <button
              type="button"
              className="fluent-toast-close-btn"
              onClick={onClose}
              aria-label="Закрыть уведомление"
            >
              <X size={12} />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
