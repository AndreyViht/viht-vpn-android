import React, { useState, useEffect } from 'react';
import { Shield, ArrowRight, ExternalLink, Loader2 } from 'lucide-react';
import SocialIcon from './SocialIcon';

export default function LoginScreen() {
  const [waitingBrowser, setWaitingBrowser] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => window.electronAPI?.onAuthError?.((data) => {
    setWaitingBrowser(false);
    setError(data?.error || 'Не удалось завершить вход. Повторите попытку.');
  }), []);

  const startBrowserAuth = async (provider = 'yandex') => {
    try {
      setWaitingBrowser(true);
      setError('');
      try {
        localStorage.setItem('viht_chosen_provider', provider);
      } catch {}

      if (window.electronAPI?.startAuthServer) {
        const { port, callbackUrl } = await window.electronAPI.startAuthServer(provider);
        const loginUrl = `https://anviht.ru/lk?desktop_callback=${encodeURIComponent(callbackUrl)}&provider=${provider}`;
        window.electronAPI.openExternal(loginUrl);
      } else {
        // Dev fallback in standard browser
        window.open(`https://anviht.ru/lk?provider=${provider}`, '_blank');
      }
    } catch (err) {
      console.error('Failed to start auth server:', err);
      setWaitingBrowser(false);
      setError('Не удалось открыть вход в браузере. Повторите попытку.');
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
      <div className="login-screen__glow-circle"></div>

      <div className="login-screen__header">
        <div className="login-logo-wrap">
          <Shield className="login-logo-icon" size={36} />
          <div className="login-logo-pulse"></div>
        </div>
        <h1 className="login-title">VihtVPN</h1>
        <p className="login-subtitle">Скоростной приватный доступ в один клик</p>
      </div>

      {waitingBrowser ? (
        <div className="login-waiting-card">
          <Loader2 className="login-spinner" size={40} />
          <h3 className="login-waiting-title">Авторизация в браузере...</h3>
          <p className="login-waiting-desc">
            Мы открыли страницу входа в вашем браузере. Завершите авторизацию через Яндекс или ВК, и приложение подключится автоматически.
          </p>
          <button 
            type="button" 
            className="btn btn--secondary btn--full"
            onClick={() => { window.electronAPI?.cancelAuthServer?.(); setWaitingBrowser(false); }}
          >
            Отмена
          </button>
        </div>
      ) : (
        <div className="login-actions">
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
