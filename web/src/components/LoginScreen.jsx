import React from 'react';
import { Shield } from 'lucide-react';
import { JoinInviteButton } from './SubscriptionInvite';

export default function LoginScreen({ removalNotice = false, onDismissRemoval }) {
  return (
    <div className="login-screen">
      {removalNotice && (
        <div role="alert" className="guest-access-notice">
          Ваш доступ к подписке отключён. Для входа потребуется новый код.
          <button type="button" aria-label="Закрыть уведомление" onClick={onDismissRemoval}>×</button>
        </div>
      )}
      <div className="login-screen__header">
        <div className="login-logo-wrap"><Shield className="login-logo-icon" size={36} /></div>
        <h1 className="login-title">Viht VPN TV</h1>
        <p className="login-subtitle">Создайте код в Viht на телефоне или компьютере в разделе «Добавить клиента».</p>
      </div>
      <div className="login-actions"><JoinInviteButton /></div>
    </div>
  );
}
