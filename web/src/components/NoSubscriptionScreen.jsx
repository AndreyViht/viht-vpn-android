import React from 'react';
import { AlertCircle, ExternalLink, RefreshCw, LogOut } from 'lucide-react';
import { getAuthProviderInfo } from './TitleBar';

export default function NoSubscriptionScreen({ user, onRefresh, onLogout }) {
  const openTariffs = () => {
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal('https://anviht.ru/#pricing');
    } else {
      window.open('https://anviht.ru/#pricing', '_blank');
    }
  };

  const authInfo = getAuthProviderInfo(user);
  const userDisplayName = authInfo?.account || user?.yandex_name || user?.yandex_email || (user?.telegram_username ? `@${user.telegram_username}` : 'Пользователь');

  return (
    <div className="no-sub-screen">
      <div className="no-sub-card">
        <div className="no-sub-icon-wrap">
          <AlertCircle size={44} className="no-sub-icon" />
        </div>

        <h2 className="no-sub-title">Подписка не активна</h2>
        <p className="no-sub-desc">
          Аккаунт <strong>{userDisplayName}</strong> успешно подключён, но у вас пока нет активной подписки VihtVPN.
        </p>

        <div className="no-sub-benefits">
          <div className="no-sub-benefit-item">
            <span className="no-sub-check">✓</span>
            <span>Безлимитный скоростной трафик (300+ Мбит/с)</span>
          </div>
          <div className="no-sub-benefit-item">
            <span className="no-sub-check">✓</span>
            <span>YouTube без рекламы и без замедления</span>
          </div>
          <div className="no-sub-benefit-item">
            <span className="no-sub-check">✓</span>
            <span>Серверы в Германии, Нидерландах, Швеции</span>
          </div>
        </div>

        <button 
          type="button" 
          className="btn btn--primary btn--full btn--glow"
          onClick={openTariffs}
        >
          <span>Оформить подписку на сайте</span>
          <ExternalLink size={16} />
        </button>

        <div className="no-sub-secondary-actions">
          <button 
            type="button" 
            className="btn btn--secondary btn--sm"
            onClick={onRefresh}
          >
            <RefreshCw size={14} />
            <span>Проверить снова</span>
          </button>
          <button 
            type="button" 
            className="btn btn--secondary btn--sm"
            onClick={onLogout}
          >
            <LogOut size={14} />
            <span>Сменить аккаунт</span>
          </button>
        </div>
      </div>
    </div>
  );
}
