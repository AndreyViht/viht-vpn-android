import React from 'react';
import { AlertTriangle, X, ShieldAlert, Check } from 'lucide-react';

export default function ForeignVpnModal({ detection, onClose, lang = 'ru' }) {
  if (!detection || !detection.hasForeignVpn) return null;

  const names = detection.names && detection.names.length > 0
    ? detection.names.join(', ')
    : (detection.summary || 'Сторонний VPN');

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet foreign-vpn-sheet" onClick={(e) => e.stopPropagation()} style={{
        maxWidth: '480px',
        margin: 'auto',
        borderRadius: '16px',
        background: 'rgba(18, 24, 38, 0.98)',
        border: '1px solid rgba(255, 255, 255, 0.12)',
        boxShadow: '0 20px 60px rgba(0, 0, 0, 0.7)',
      }}>
        {/* Header */}
        <div className="modal-header" style={{ padding: '20px 24px 16px', borderBottom: '1px solid rgba(255, 255, 255, 0.08)' }}>
          <div className="modal-title-wrap" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              background: 'rgba(245, 158, 11, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#f59e0b',
            }}>
              <ShieldAlert size={20} />
            </div>
            <h3 className="modal-title" style={{ margin: 0, fontSize: '17px', fontWeight: 600, color: '#fff' }}>
              {lang === 'en' ? 'Active VPN Connection Detected' : 'Обнаружено VPN-подключение'}
            </h3>
          </div>

          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'rgba(255, 255, 255, 0.5)',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '6px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body Content */}
        <div style={{ padding: '20px 24px' }}>
          <div style={{
            background: 'rgba(245, 158, 11, 0.08)',
            border: '1px solid rgba(245, 158, 11, 0.2)',
            borderRadius: '10px',
            padding: '12px 14px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
          }}>
            <AlertTriangle size={18} color="#f59e0b" style={{ flexShrink: 0 }} />
            <div style={{ fontSize: '13px', color: '#fbbf24' }}>
              <strong>{lang === 'en' ? 'Conflicting software:' : 'Активный сторонний сервис:'}</strong> {names}
            </div>
          </div>

          <p style={{ margin: '0 0 12px', fontSize: '14px', lineHeight: '1.5', color: 'rgba(255, 255, 255, 0.9)' }}>
            {lang === 'en'
              ? 'Another VPN connection is currently active on your computer. This may conflict with Viht.'
              : 'Сейчас на компьютере уже работает другое VPN-соединение. Это может конфликтовать с Viht.'}
          </p>

          <p style={{ margin: '0 0 24px', fontSize: '13px', lineHeight: '1.5', color: 'rgba(255, 255, 255, 0.65)' }}>
            {lang === 'en'
              ? 'You can continue using Viht. Before connecting, the conflicting VPN will be safely deactivated.'
              : 'Вы можете продолжить работу с Viht, но перед подключением сторонний VPN будет отключён.'}
          </p>

          {/* Action button */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                width: '100%',
                padding: '12px 20px',
                background: '#10b981',
                color: '#fff',
                border: 'none',
                borderRadius: '10px',
                fontSize: '14px',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'background 0.2s ease',
              }}
            >
              {lang === 'en' ? 'Got it' : 'Понятно'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
