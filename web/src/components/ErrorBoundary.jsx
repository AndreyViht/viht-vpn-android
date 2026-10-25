import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('[VihtVPN React Crash]', error, errorInfo);
    this.setState({ errorInfo });
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    try {
      window.location.reload();
    } catch {}
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: '#0b0f19',
          color: '#ffffff',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '24px',
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
          zIndex: 99999,
        }}>
          <div style={{
            background: 'rgba(18, 25, 42, 0.95)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            borderRadius: '16px',
            padding: '32px',
            maxWidth: '460px',
            textAlign: 'center',
            boxShadow: '0 20px 60px rgba(0,0,0,0.8)',
          }}>
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              background: 'rgba(239, 68, 68, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
              color: '#ef4444',
            }}>
              <AlertCircle size={28} />
            </div>
            <h2 style={{ fontSize: '18px', fontWeight: 600, margin: '0 0 8px', color: '#fff' }}>
              Ошибка отображения интерфейса
            </h2>
            <p style={{ fontSize: '13px', color: 'rgba(255, 255, 255, 0.7)', margin: '0 0 20px', lineHeight: 1.5 }}>
              VPN продолжает работать в фоновом режиме. Нажмите кнопку ниже, чтобы перезагрузить интерфейс.
            </p>
            <button
              type="button"
              onClick={this.handleReload}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                background: '#10b981',
                color: '#fff',
                border: 'none',
                borderRadius: '10px',
                padding: '10px 20px',
                fontSize: '14px',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'background 0.2s',
              }}
            >
              <RefreshCw size={16} />
              <span>Восстановить интерфейс</span>
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
