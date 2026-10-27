import React from 'react';
import { X, Check, Zap, Globe, RefreshCw } from 'lucide-react';
import CountryFlag from './CountryFlag';
import { formatServerPing, getServerCountry } from '../services/servers';

export default function ServerSelectorModal({
  servers = [],
  currentServer,
  onSelectServer,
  onRefreshKeys,
  refreshingKeys = false,
  onRefreshPing,
  refreshingPing = false,
  onClose,
}) {
  const getPingClass = (ping) => {
    if (ping < 45) return 'ping-badge--excellent';
    if (ping < 80) return 'ping-badge--good';
    return 'ping-badge--medium';
  };

  const getCountryCode = getServerCountry;

  const cleanServerName = (rawName) => {
    if (!rawName) return '';
    let str = rawName;
    // Strip regional indicator flag emojis (e.g. 🇩🇪 🇳🇱 🇸🇪 🇷🇺)
    str = str.replace(/[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]/g, '');
    // Strip leading text codes like "DE", "NL", "SE", "RU", "EU"
    str = str.replace(/^(DE|NL|SE|RU|EU)\s+/gi, '');
    str = str.replace(/^(DE|NL|SE|RU|EU)\s+/gi, '');
    // Trim leading spaces, dashes, dots
    str = str.trim().replace(/^[-–—•\s]+/, '').trim();
    return str || rawName;
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet" onClick={(e) => e.stopPropagation()}>
        {/* Header with Title and Minimalist Action Icons */}
        <div className="modal-header">
          <div className="modal-title-wrap">
            <Globe size={17} className="modal-title-icon" />
            <h3 className="modal-title">Выбор локации</h3>
          </div>

          <div className="modal-header-actions">
            <button
              type="button"
              className={`modal-header-icon-btn ${refreshingKeys ? 'active' : ''}`}
              onClick={onRefreshKeys}
              disabled={refreshingKeys}
              title="Обновить ключи с сервера"
            >
              <RefreshCw size={14} className={refreshingKeys ? 'spinning' : ''} />
            </button>

            <button
              type="button"
              className={`modal-header-icon-btn ${refreshingPing ? 'active' : ''}`}
              onClick={onRefreshPing}
              disabled={refreshingPing}
              title="Замерить пинг до серверов"
            >
              <Zap size={14} className={refreshingPing ? 'spinning' : ''} />
            </button>

            <button className="modal-close-btn" onClick={onClose} title="Закрыть">
              <X size={17} />
            </button>
          </div>
        </div>

        {/* Minimalist Server List */}
        <div className="server-list">
          {servers.map((server) => {
            const isSelected = currentServer?.id === server.id;
            const country = getCountryCode(server);
            const displayName = cleanServerName(server.name);

            return (
              <div
                key={server.id}
                className={`server-item ${isSelected ? 'server-item--active' : ''}`}
                onClick={() => {
                  onSelectServer(server);
                  onClose();
                }}
              >
                <div className="server-item__flag-wrap">
                  <CountryFlag country={country} />
                </div>

                <div className="server-item__info">
                  <div className="server-item__name-row">
                    <span className="server-item__name">{displayName}</span>
                    {server.isRecommended && (
                      <span className="server-tag server-tag--recommended">Топ</span>
                    )}
                    {server.isYouTubeNoAds && (
                      <span className="server-tag server-tag--youtube">Без рекламы</span>
                    )}
                  </div>
                </div>

                <div className="server-item__right">
                  <div className={`ping-badge ${getPingClass(server.ping)}`}>
                    <span className="ping-dot"></span>
                    <span>{formatServerPing(server)}</span>
                  </div>
                  {isSelected && (
                    <div className="server-item__check">
                      <Check size={15} />
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
