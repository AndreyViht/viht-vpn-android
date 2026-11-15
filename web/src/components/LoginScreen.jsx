import {JoinInviteButton} from './SubscriptionInvite';
import React, { useState, useEffect } from 'react';
import { Shield, ArrowRight, ExternalLink, Loader2 } from 'lucide-react';
import SocialIcon from './SocialIcon';

export default function LoginScreen({removalNotice=false,onDismissRemoval}) {
  const [waitingBrowser, setWaitingBrowser] = useState(false);
  const [nativeWaiting, setNativeWaiting] = useState(false);
  const [browserFallback, setBrowserFallback] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => window.electronAPI?.onAuthError?.((data) => {
    setWaitingBrowser(false);
    setNativeWaiting(false);
    setBrowserFallback(Boolean(data?.yandexNative && !data?.cancelled));
    if (data?.cancelled) return;
    setError(data?.error || 'Не удалось завершить вход. Повторите попытку.');
  }), []);

  const startBrowserAuth = async (provider = 'yandex', forceBrowser = false) => {
    try {
      setWaitingBrowser(true);
      setError('');
      try {
        localStorage.setItem('viht_chosen_provider', provider);
      } catch {}

      if (provider === 'yandex' && !forceBrowser && window.electronAPI?.startNativeYandex) {
        setNativeWaiting(true);
        const native = await window.electronAPI.startNativeYandex();
        if (native?.available) return;
        setNativeWaiting(false);
        if (native?.ok === false) {
          setBrowserFallback(true);
          throw new Error('Не удалось открыть Яндекс ID.');
        }
      }

      if (window.electronAPI?.startAuthServer) {
        const result = await window.electronAPI.startAuthServer(provider);
        const callbackUrl = result?.callbackUrl;
        if (!callbackUrl) throw new Error(result?.error || "Не удалось подготовить вход");
        const loginUrl = `https://anviht.ru/lk?desktop_callback=${encodeURIComponent(callbackUrl)}&provider=${provider}`;
        const opened = await window.electronAPI.openExternal(loginUrl);
        if (opened?.ok === false) throw new Error(opened.error || "Не удалось открыть браузер");
      } else {
        // Dev fallback in standard browser
        window.open(`https://anviht.ru/lk?provider=${provider}`, '_blank');
      }
    } catch (err) {
      console.error('Failed to start auth server:', err);
      setWaitingBrowser(false);
      window.electronAPI?.cancelAuthServer?.();
      setError(err.message || 'Не удалось открыть вход в браузере. Повторите попытку.');
    }
  };

  const openWebsite = () => {
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal('https://anviht.ru');
    } else {
      window.open('https://anviht.ru', '_blank');
    }
  };

  return (
    <div className="login-screen">
      {removalNotice && <div role="alert" className="guest-access-notice">Вас удалили из подписки. Подключитесь заново к прежнему или другому владельцу.<button type="button" aria-label="Закрыть уведомление" onClick={onDismissRemoval}>×</button></div>}
      <div className="login-screen__glow-circle"></div>

      <div className="login-screen__header">
        <div className="login-logo-wrap">
          <Shield className="login-logo-icon" size={36} />
          <div className="login-logo-pulse"></div>
        </div>
        <h1 className="login-title">Viht</h1>
        <p className="login-subtitle">Скоростной приватный доступ в один клик</p>
      </div>

      {waitingBrowser ? (
        <div className="login-waiting-card">
          <Loader2 className="login-spinner" size={40} />
          <h3 className="login-waiting-title">{nativeWaiting ? 'Вход через Яндекс ID…' : 'Авторизация в браузере…'}</h3>
          <p className="login-waiting-desc">
            {nativeWaiting ? 'Завершите вход в Яндекс ID.' : 'Завершите вход в браузере, чтобы открыть свой аккаунт в Viht.'}
          </p>
          <button 
            type="button" 
            hidden={nativeWaiting}
            className="btn btn--secondary btn--full"
            onClick={() => { window.electronAPI?.cancelAuthServer?.(); setWaitingBrowser(false); }}
          >
            Отмена
          </button>
        </div>
      ) : (
        <div className="login-actions">
          <JoinInviteButton />
          {/* Кнопка входа через Яндекс */}
          <button
            type="button"
            className="btn-auth btn-auth--yandex"
            onClick={() => startBrowserAuth('yandex')}
          >
            <div className="btn-auth__icon-wrap">
              <SocialIcon type="yandex" size={36} />
            </div>
            <div className="btn-auth__text">
              <span className="btn-auth__title">Войти через Яндекс ID</span>
              <span className="btn-auth__hint">С вашей подпиской на сайте</span>
            </div>
            <ArrowRight size={18} className="btn-auth__arrow" />
          </button>

          {/* Кнопка входа через ВКонтакте */}
          <button
            type="button"
            className="btn-auth btn-auth--vk"
            onClick={() => startBrowserAuth('vk')}
          >
            <div className="btn-auth__icon-wrap">
              <SocialIcon type="vk" size={36} />
            </div>
            <div className="btn-auth__text">
              <span className="btn-auth__title">Войти через VK ID</span>
              <span className="btn-auth__hint">Быстрая авторизация</span>
            </div>
            <ArrowRight size={18} className="btn-auth__arrow" />
          </button>

          {error && <p className="login-error-text" role="alert">{error}</p>}
          {browserFallback && <button type="button" className="login-link-btn" onClick={()=>startBrowserAuth('yandex', true)}>Войти через сайт</button>}
          <div className="login-footer">
            <span>Ещё нет аккаунта?</span>
            <button type="button" className="login-link-btn" onClick={openWebsite}>
              <span>Зарегистрироваться на anviht.ru</span>
              <ExternalLink size={12} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
