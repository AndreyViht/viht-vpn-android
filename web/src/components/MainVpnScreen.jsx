import React from 'react';
import { ChevronRight, Power } from 'lucide-react';
import CountryFlag from './CountryFlag';
import { SessionTimer } from './LiveVpnValues';
import { getServerCountry } from '../services/servers';
import { t } from '../utils/i18n';

const labels = {
  ru: { connected: 'VPN подключён', disconnected: 'VPN выключен', connecting: 'Подключение…', disconnecting: 'Отключение…', error: 'Ошибка подключения' },
  en: { connected: 'VPN connected', disconnected: 'VPN off', connecting: 'Connecting…', disconnecting: 'Disconnecting…', error: 'Connection error' },
};

export default function MainVpnScreen({
  accessDisabled, currentServer, connectionState, connectedAt,
  onToggleConnection, onOpenServerSelector, lang = 'ru',
}) {
  const connected = connectionState === 'connected';
  const busy = connectionState === 'connecting' || connectionState === 'disconnecting';
  const status = labels[lang] || labels.ru;
  return (
    <section className="tv-home" aria-label="VPN">
      <div className="tv-home-status">
        <h1 role="status">{status[connectionState] || status.disconnected}</h1>
        <div className="tv-home-session">
          {connected && <>{t('session_label', lang)} <strong><SessionTimer connectedAt={connectedAt} /></strong></>}
        </div>
      </div>
      {accessDisabled && <p className="tv-home-notice" role="alert">Доступ отключён владельцем подписки.</p>}
      <div className="tv-home-actions">
        <button type="button" className="tv-home-power" disabled={busy || (!connected && (!currentServer || accessDisabled))} onClick={onToggleConnection}>
          <Power size={22} aria-hidden="true" />
          {t(connected ? 'btn_disconnect' : 'btn_connect', lang)}
        </button>
        <button type="button" className="tv-home-server" onClick={onOpenServerSelector}>
          {currentServer && <CountryFlag country={getServerCountry(currentServer)} />}
          <span>{currentServer?.name || (lang === 'en' ? 'Choose a server' : 'Выбрать сервер')}</span>
          <ChevronRight size={19} aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}
