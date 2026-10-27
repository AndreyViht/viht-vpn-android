import React from 'react';
import { Minus, X, Shield, HelpCircle } from 'lucide-react';
import SocialIcon from './SocialIcon';
import { isMobile } from '../utils/platform';

export function getAuthProviderInfo(user, preferredProvider = null) {
  if (!user) return null;

  const auths = Array.isArray(user.authorizations) ? user.authorizations : [];
  const vkAuth = auths.find((a) => a.provider === 'vk');
  const yandexAuth = auths.find((a) => a.provider === 'yandex');
  const tgAuth = auths.find((a) => a.provider === 'telegram');

  let chosen = (
    preferredProvider ||
    user.chosen_provider ||
    user.auth_provider ||
    (typeof window !== 'undefined' ? localStorage.getItem('viht_chosen_provider') : null) ||
    user.current_provider ||
    ''
  ).toLowerCase();

  // If chosen is not set, deduce from authorizations
  if (!chosen) {
    if (vkAuth && !yandexAuth) chosen = 'vk';
    else if (tgAuth && !yandexAuth && !vkAuth) chosen = 'telegram';
    else if (yandexAuth) chosen = 'yandex';
  }

  // 1. VK ID
  if (chosen === 'vk' || chosen === 'custom:vk') {
    const vkName = vkAuth?.name || user.vk_name || (user.vk_id ? `id${user.vk_id}` : (user.vk_ids?.[0] ? `id${user.vk_ids[0]}` : 'VK ID'));
    return {
      type: 'vk',
      label: 'VK ID',
      badge: 'VK',
      account: vkName,
      subAccount: vkAuth?.identifier || (user.vk_id ? `id${user.vk_id}` : ''),
      avatar_url: vkAuth?.avatar_url || null,
    };
  }

  // 2. Telegram
  if (chosen === 'telegram' || chosen === 'tg') {
    const tgName = tgAuth?.name || (user.telegram_username ? `@${user.telegram_username.replace(/^@/, '')}` : (user.telegram_id ? `ID: ${user.telegram_id}` : 'Telegram'));
    return {
      type: 'tg',
      label: 'Telegram',
      badge: 'TG',
      account: tgName,
      subAccount: user.telegram_id ? `ID: ${user.telegram_id}` : '',
      avatar_url: null,
    };
  }

  // 3. Yandex ID
  if (chosen === 'yandex' || chosen === 'custom:yandex') {
    const yName = yandexAuth?.identifier || yandexAuth?.email || user.yandex_email || user.yandex_name || yandexAuth?.name || 'Яндекс ID';
    return {
      type: 'yandex',
      label: 'Яндекс ID',
      badge: 'Я',
      account: yName,
      subAccount: user.yandex_name || yandexAuth?.name || '',
      avatar_url: yandexAuth?.avatar_url || user.yandex_avatar_url || null,
    };
  }

  // Fallbacks if chosen is unrecognized
  if (vkAuth || user.vk_id || (user.vk_ids && user.vk_ids.length > 0)) {
    const vkName = vkAuth?.name || user.vk_name || (user.vk_id ? `id${user.vk_id}` : `id${user.vk_ids[0]}`);
    return {
      type: 'vk',
      label: 'VK ID',
      badge: 'VK',
      account: vkName,
      subAccount: vkAuth?.identifier || '',
      avatar_url: vkAuth?.avatar_url || null,
    };
  }

  const email = (user.yandex_email || user.email || '').toLowerCase();
  if (email.includes('yandex') || email.includes('ya.ru') || user.yandex_email || yandexAuth) {
    return {
      type: 'yandex',
      label: 'Яндекс ID',
      badge: 'Я',
      account: email || user.yandex_email || 'Яндекс ID',
      subAccount: user.yandex_name || '',
      avatar_url: yandexAuth?.avatar_url || null,
    };
  }

  if (user.telegram_id || user.telegram_username || tgAuth) {
    return {
      type: 'tg',
      label: 'Telegram',
      badge: 'TG',
      account: user.telegram_username ? `@${user.telegram_username.replace(/^@/, '')}` : 'Telegram',
      avatar_url: null,
    };
  }

  return {
    type: 'cabinet',
    label: 'Кабинет',
    badge: 'LK',
    account: email || 'Личный кабинет',
  };
}

function TitleBar({ title = "VihtVPN", isConnected = false, user = null, onOpenHelp, minimizeToTray = true }) {
  const handleMinimize = () => {
    if (window.electronAPI?.minimizeWindow) {
      window.electronAPI.minimizeWindow();
    }
  };

  const handleClose = () => {
    if (window.electronAPI?.closeWindow) {
      window.electronAPI.closeWindow();
    }
  };

  const authInfo = getAuthProviderInfo(user);

  return (
    <div className={`titlebar ${isMobile ? 'titlebar--mobile' : ''}`}>
      <div className="titlebar-drag-region">
        <div className="titlebar-brand">
          <div className={`titlebar-icon-wrap ${isConnected ? 'connected' : ''}`}>
            <Shield className="titlebar-icon" size={14} />
          </div>
          <span className="titlebar-title">{title}</span>
          {!isMobile && <span className="titlebar-badge">Desktop</span>}

          {authInfo && (
            <div
              className={`titlebar-auth-pill titlebar-auth-pill--${authInfo.type}`}
              title={`Авторизован: ${authInfo.label} (${authInfo.account})`}
            >
              <SocialIcon type={authInfo.type} size={15} shape="circle" />
              <span className="titlebar-auth-text">{authInfo.label}</span>
            </div>
          )}
        </div>
      </div>

      {!isMobile && (
        <div className="titlebar-controls">
          <button className="titlebar-btn" onClick={onOpenHelp} title="Помощь" aria-label="Помощь">
            <HelpCircle size={15} />
          </button>
          <button 
            className="titlebar-btn titlebar-btn--minimize" 
            onClick={handleMinimize} 
            title="Свернуть"
          >
            <Minus size={14} />
          </button>
          <button 
            className="titlebar-btn titlebar-btn--close" 
            onClick={handleClose} 
            title={minimizeToTray ? 'Свернуть в трей' : 'Закрыть программу'}
          >
            <X size={14} />
          </button>
        </div>
      )}
    </div>
  );
}

export default React.memo(TitleBar);
