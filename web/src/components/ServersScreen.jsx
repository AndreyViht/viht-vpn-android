import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Globe,
  RefreshCw,
  Activity,
  Check,
  Zap,
} from 'lucide-react';
import CountryFlag from './CountryFlag';
import { t } from '../utils/i18n';
import { springs, buttonPress } from '../utils/motion';
import { formatServerPing, getServerCountry } from '../services/servers';

function ServersScreen({
  servers,
  currentServer,
  connectionState,
  onSelectServer,
  onRefreshKeys,
  refreshingKeys,
  onRefreshPing,
  refreshingPing,
  lang = 'ru',
}) {
  const getCountryCode = getServerCountry;

  const getPingColorClass = (ping) => {
    if (!ping || ping <= 0) return 'gray';
    if (ping < 60) return 'green';
    if (ping < 120) return 'yellow';
    return 'orange';
  };

  const cleanServerName = (rawName) => {
    if (!rawName) return '';
    let str = rawName;
    str = str.replace(/[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]/g, '');
    str = str.replace(/^(DE|NL|SE|RU|EU)\s+/gi, '');
    str = str.trim().replace(/^[-–—•\s]+/, '').trim();
    return str || rawName;
  };

  const availableServers = servers || [];

  return (
    <div className="fluent-page fluent-servers-page">
      {/* Header */}
      <div className="fluent-page-header">
        <div className="fluent-page-header__actions">
          <motion.button data-motion-press="true"
            type="button"
            className="btn btn--secondary btn--sm"
            onClick={onRefreshPing}
            disabled={refreshingPing}
            title={lang === 'en' ? 'TCP connection latency, without a proxy URL test' : 'Задержка TCP-соединения, без проверки URL через прокси'}
            {...buttonPress}
          >
            <Activity size={14} className={refreshingPing ? 'shimmer-pulse' : ''} />
            <span>{refreshingPing ? t('btn_pinging', lang) : (lang === 'en' ? 'TCP ping' : 'TCP-пинг')}</span>
          </motion.button>

          <motion.button data-motion-press="true"
            type="button"
            className="btn btn--secondary btn--sm"
            onClick={onRefreshKeys}
            disabled={refreshingKeys}
            title={t('btn_sync', lang)}
            {...buttonPress}
          >
            <RefreshCw size={14} className={refreshingKeys ? 'spinning' : ''} />
            <span>{refreshingKeys ? t('btn_syncing', lang) : t('btn_sync', lang)}</span>
          </motion.button>
        </div>
      </div>

      {/* Servers List with Shared Layout Spring Selection */}
      <div className="fluent-servers-list">
        {availableServers.length === 0 ? (
          <div className="fluent-empty-state" style={{ padding: '36px 16px', textAlign: 'center', opacity: 0.7 }}>
            <p style={{ fontWeight: 600, fontSize: '15px', marginBottom: '6px' }}>{lang === 'en' ? 'No available keys' : 'Нет доступных ключей'}</p>
            <p style={{ fontSize: '13px' }}>{lang === 'en' ? 'Sync your subscription to load the current server list.' : 'Синхронизируйте подписку, чтобы загрузить актуальный список.'}</p>
          </div>
        ) : (
          availableServers.map((server) => {
            const isSelected = server.id === currentServer?.id;
            const countryCode = getCountryCode(server);
            const pingClass = getPingColorClass(server.ping);

            return (
              <motion.button data-motion-press="true"
                type="button"
                aria-pressed={isSelected}
                key={server.id}
                className={`fluent-server-row ${isSelected ? 'fluent-server-row--selected' : ''}`}
                onClick={() => onSelectServer(server)}
                whileHover={{ y: -1, scale: 1.002 }}
                whileTap={{ scale: 0.985 }}
                transition={springs.tactile}
              >
                {/* Active border glow for selected card */}
                {isSelected && (
                  <motion.div
                    className="fluent-server-selected-glow"
                    layoutId="server-active-outline"
                    transition={springs.natural}
                  />
                )}

                <div className="fluent-server-row__flag">
                  <CountryFlag country={countryCode} />
                </div>

                <div className="fluent-server-row__details">
                  <div className="fluent-server-row__name-line">
                    <strong className="fluent-server-row__name">{cleanServerName(server.name)}</strong>
                    {server.isRecommended && (
                      <span className="fluent-chip fluent-chip--top">
                        <Zap size={10} /> {t('best_ping', lang)}
                      </span>
                    )}
                    {server.isYouTubeNoAds && (
                      <span className="fluent-chip fluent-chip--yt">{t('no_ads', lang)}</span>
                    )}
                    {server.isLte && (
                      <span className="fluent-chip fluent-chip--lte">LTE CDN</span>
                    )}
                  </div>
                </div>

                <div className="fluent-server-row__meta">
                  <div className={`fluent-server-row__ping-box ${refreshingPing ? 'shimmer-active' : ''}`} title={lang === 'en' ? 'TCP connection latency' : 'Задержка TCP-соединения'}>
                    <span className={`ping-status-dot ${pingClass}`} />
                    <span className="fluent-server-row__ping-val">{formatServerPing(server, lang)}</span>
                  </div>

                  <div className={`fluent-server-radio ${isSelected ? 'selected' : ''}`}>
                    <AnimatePresence>
                      {isSelected && (
                        <motion.div
                          initial={{ scale: 0, opacity: 0 }}
                          animate={{ scale: 1, opacity: 1 }}
                          exit={{ scale: 0, opacity: 0 }}
                          transition={springs.tactile}
                        >
                          <Check size={13} />
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                </div>
              </motion.button>
            );
          })
        )}
      </div>
    </div>
  );
}

export default React.memo(ServersScreen);
