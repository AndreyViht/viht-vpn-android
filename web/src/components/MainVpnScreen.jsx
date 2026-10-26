import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Power,
  Shield,
  ShieldCheck,
  ChevronRight,
  ArrowDown,
  ArrowUp,
  Clock,
  Activity,
  Globe,
  CreditCard,
  Lock,
  ArrowDownUp,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';
import CountryFlag from './CountryFlag';
import {SessionTimer,TransferValue} from './LiveVpnValues';
import { t } from '../utils/i18n';
import { springs, buttonPress, cardPress } from '../utils/motion';
import { isAndroid } from '../utils/platform';
import { formatServerPing, getServerCountry } from '../services/servers';

function MainVpnScreen({
  user,
  currentServer,
  connectionState, // 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'error'
  connectedAt,
  onToggleConnection,
  onOpenServerSelector,
  lang = 'ru',
}) {
  const isConnected = connectionState === 'connected';
  const isConnecting = connectionState === 'connecting';
  const isDisconnecting = connectionState === 'disconnecting';
  const isError = connectionState === 'error';
  const [,refreshSubscriptionTime]=useState(0);
  useEffect(()=>{const timer=setInterval(()=>{if(!document.hidden)refreshSubscriptionTime(value=>value+1)},60000);return()=>clearInterval(timer)},[]);

  // Subscription calculation
  const getDaysLeft = (u) => {
    if (!u) return 0;
    if (u.subscription_expires_at) {
      const diff = new Date(u.subscription_expires_at).getTime() - Date.now();
      if (diff > 0) {
        return Math.floor(diff / (1000 * 60 * 60 * 24));
      }
      return 0;
    }
    return u.subscription_days_left != null ? Number(u.subscription_days_left) : 0;
  };

  const formatSubTime = (u) => {
    if (!u) return '—';
    if (u.subscription_expires_at) {
      const diff = new Date(u.subscription_expires_at).getTime() - Date.now();
      if (diff > 0) {
        const totalHours = Math.floor(diff / (1000 * 60 * 60));
        const d = Math.floor(totalHours / 24);
        const h = totalHours % 24;
        return `${d} ${t('days_short', lang)} ${h} ${t('hours_short', lang)}`;
      }
      return t('sub_expired_label', lang);
    }
    const d = u.subscription_days_left ?? 0;
    const h = u.subscription_hours_left ?? 0;
    return `${d} ${t('days_short', lang)} ${h} ${t('hours_short', lang)}`;
  };

  const daysLeft = getDaysLeft(user);

  let renewConfig = {
    className: 'renew-btn--ok',
    tooltip: t('renew_all_good', lang),
    disabled: true,
    showShine: false,
  };

  if (daysLeft >= 15) {
    renewConfig = {
      className: 'renew-btn--ok',
      tooltip: t('renew_all_good', lang),
      disabled: true,
      showShine: false,
    };
  } else if (daysLeft >= 9) {
    renewConfig = {
      className: 'renew-btn--warn',
      tooltip: t('renew_can_pay', lang),
      disabled: false,
      showShine: false,
    };
  } else if (daysLeft >= 3) {
    renewConfig = {
      className: 'renew-btn--urgent',
      tooltip: t('renew_time_to_pay', lang),
      disabled: false,
      showShine: true,
    };
  } else if (daysLeft > 0) {
    renewConfig = {
      className: 'renew-btn--critical',
      tooltip: t('renew_almost_expired', lang),
      disabled: false,
      showShine: true,
    };
  } else {
    renewConfig = {
      className: 'renew-btn--expired',
      tooltip: t('renew_expired', lang),
      disabled: false,
      showShine: true,
    };
  }

  const handleOpenRenew = () => {
    if (renewConfig.disabled) return;
    const pricingUrl = 'https://anviht.ru/#pricing';
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal(pricingUrl);
    } else {
      window.open(pricingUrl, '_blank');
    }
  };

  const getCountryCode = getServerCountry;

  const cleanServerName = (rawName) => {
    if (!rawName) return '';
    let str = rawName;
    str = str.replace(/[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]/g, '');
    str = str.replace(/^(DE|NL|SE|RU|EU)\s+/gi, '');
    str = str.trim().replace(/^[-–—•\s]+/, '').trim();
    return str || rawName;
  };

  return (
    <div className="fluent-main-screen">
      <div className="fluent-main-content">
        {/* ─── 1. Selected Server Card (Squircle, Micro-interactions) ─── */}
        <motion.div
          className="fluent-card fluent-server-select-card"
          onClick={onOpenServerSelector}
          role="button"
          tabIndex={0}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onOpenServerSelector();
            }
          }}
          title={t('click_to_change_loc', lang)}
          {...cardPress}
        >
          <div className="fluent-server-card__flag">
            <CountryFlag country={getCountryCode(currentServer)} />
          </div>

          <div className="fluent-server-card__info">
            <div className="fluent-server-card__title-row">
              <span className="fluent-server-card__name">{currentServer ? cleanServerName(currentServer.name) : (lang === 'en' ? 'No available keys' : 'Нет доступных ключей')}</span>
              {currentServer?.isYouTubeNoAds && (
                <span className="fluent-chip fluent-chip--yt">{t('no_ads', lang)}</span>
              )}
              {currentServer?.isLte && (
                <span className="fluent-chip fluent-chip--lte">LTE</span>
              )}
            </div>
            <span className="fluent-server-card__subtitle">
              {t('click_to_select_server', lang)}
            </span>
          </div>

          <div className="fluent-server-card__action">
            <div className="fluent-server-card__ping">
              <span className={`ping-status-dot ${currentServer?.ping == null ? 'gray' : currentServer.ping < 65 ? 'green' : 'yellow'}`} />
              <span>{formatServerPing(currentServer, lang)}</span>
            </div>
            <ChevronRight size={16} className="fluent-chevron-icon" />
          </div>
        </motion.div>

        {/* ─── 2. Subscription Card ─── */}
        <motion.div
          className="fluent-card fluent-subscription-card"
          initial={false}
          {...cardPress}
        >
          <div className="fluent-sub-info">
            <div className="fluent-sub-icon-wrap">
              <Shield size={16} className="fluent-sub-icon" />
            </div>
            <div className="fluent-sub-details">
              <span className="fluent-sub-label">{t('sub_validity', lang)}</span>
              <strong className="fluent-sub-value">{formatSubTime(user)}</strong>
            </div>
          </div>

          <motion.button data-motion-press="true"
            type="button"
            className={`fluent-renew-btn ${renewConfig.className} ${renewConfig.showShine ? 'fluent-renew-btn--pulse' : ''}`}
            onClick={handleOpenRenew}
            disabled={renewConfig.disabled}
            title={renewConfig.tooltip}
            whileHover={!renewConfig.disabled ? { scale: 1.03 } : {}}
            whileTap={!renewConfig.disabled ? { scale: 0.96 } : {}}
            transition={springs.tactile}
          >
            <CreditCard size={14} className="fluent-renew-btn__icon" />
            <span>{t('extend_sub', lang)}</span>
          </motion.button>
        </motion.div>

        {/* ─── 3. Unified Morphing Hero Surface (Dynamic Island style) ─── */}
        <motion.div
          className={`fluent-hero-morph-surface state-${connectionState}`}
          layout
          layoutDependency={connectionState}
          transition={springs.natural}
        >
          {/* Ambient organic glow within the surface */}
          <div className={`fluent-morph-aura state-${connectionState}`} />

          {/* Morphing Top Header Status Pill */}
          <motion.div className="fluent-morph-header" layout layoutDependency={connectionState}>
            <div className={`fluent-status-badge-pill state-${connectionState}`}>
              <span className="fluent-status-dot" />
              <span className="fluent-status-text">
                {isConnected && (lang === 'en' ? (isAndroid ? 'Protected • Sing-Box' : 'Protected • Android VPN') : (isAndroid ? 'Защищено • Sing-Box' : 'Защищено • Android VPN'))}
                {isConnecting && (lang === 'en' ? 'Connecting to server...' : 'Подключение к серверу...')}
                {isDisconnecting && (lang === 'en' ? 'Disconnecting...' : 'Отключение...')}
                {isError && (lang === 'en' ? 'Connection Error' : 'Ошибка подключения')}
                {connectionState === 'disconnected' && (lang === 'en' ? 'Unprotected' : 'Не защищено')}
              </span>
            </div>
          </motion.div>

          {/* Central Morphing Power Ring & Button */}
          <div className="fluent-hero-center">
            <div className={`fluent-power-ring-wrap state-${connectionState}`}>
              <div className="fluent-ring-ambient" />
              {isConnecting && <div className="fluent-ring-spinner" />}

              <motion.button data-motion-press="true"
                type="button"
                className={`fluent-power-btn state-${connectionState}`}
                onClick={onToggleConnection}
                disabled={isConnecting || isDisconnecting || !currentServer}
                aria-label={isConnected ? t('btn_disconnect', lang) : t('btn_connect', lang)}
                whileHover={{ scale: 1.04 }}
                whileTap={{ scale: 0.94 }}
                transition={springs.tactile}
                layout
              >
                <div className="fluent-power-btn__glass">
                  <AnimatePresence mode="wait">
                    {isConnected ? (
                      <motion.div
                        key="connected-icon"
                        initial={{ scale: 0.5, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.5, opacity: 0 }}
                        transition={springs.tactile}
                      >
                        <ShieldCheck size={54} className="fluent-power-icon fluent-power-icon--connected" />
                      </motion.div>
                    ) : isConnecting ? (
                      <motion.div
                        key="connecting-icon"
                        initial={{ opacity: 0, rotate: -30 }}
                        animate={{ opacity: 1, rotate: 0 }}
                        exit={{ opacity: 0 }}
                        transition={springs.tactile}
                      >
                        <Activity size={50} className="fluent-power-icon fluent-power-icon--connecting" />
                      </motion.div>
                    ) : isError ? (
                      <motion.div
                        key="error-icon"
                        initial={{ scale: 0.7, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.7, opacity: 0 }}
                        transition={springs.tactile}
                      >
                        <AlertTriangle size={50} className="fluent-power-icon fluent-power-icon--error" />
                      </motion.div>
                    ) : (
                      <motion.div
                        key="disconnected-icon"
                        initial={{ scale: 0.8, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.8, opacity: 0 }}
                        transition={springs.tactile}
                      >
                        <Power size={50} className="fluent-power-icon" />
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </motion.button>
            </div>

            {/* Status title & timer */}
            <motion.div className="fluent-hero-status" layout layoutDependency={connectionState}>
              <h2 className="fluent-hero-title">
                {isConnected
                  ? t('status_connected_title', lang)
                  : isConnecting
                  ? t('status_connecting_title', lang)
                  : isError
                  ? (lang === 'en' ? 'Connection Failed' : 'Сбой соединения')
                  : (lang === 'en' ? 'Ready when you are' : 'Всё готово к подключению')}
              </h2>

              <div className="fluent-hero-desc">
                {isConnected ? (
                  <motion.div
                    className="fluent-hero-timer-pill"
                    initial={{ opacity: 0, y: 4 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={springs.natural}
                  >
                    <Clock size={13} className="timer-icon" />
                    <span className="timer-label">{t('session_label', lang)}:</span>
                    <strong className="timer-digits"><SessionTimer connectedAt={connectedAt}/></strong>
                  </motion.div>
                ) : isError ? (
                  <span className="fluent-error-prompt">
                    {lang === 'en' ? 'Click to retry connection' : 'Нажмите, чтобы повторить подключение'}
                  </span>
                ) : (
                  <span>{t('hero_prompt_connect', lang)}</span>
                )}
              </div>
            </motion.div>
          </div>

          {/* Morphing Telemetry Grid (Connected State) or Protection Badges (Disconnected) */}
          <AnimatePresence mode="wait">
            {isConnected ? (
              <motion.div
                key="telemetry-block"
                className="fluent-telemetry-surface"
                initial={{ opacity: 0, height: 0, y: 10 }}
                animate={{ opacity: 1, height: 'auto', y: 0 }}
                exit={{ opacity: 0, height: 0, y: 6 }}
                transition={springs.natural}
                layout
              >
                <div className="fluent-telemetry-grid">
                  {/* Download */}
                  <div className="fluent-telemetry-item">
                    <div className="fluent-telemetry-icon-box fluent-telemetry-icon-box--down">
                      <ArrowDown size={14} />
                    </div>
                    <div className="fluent-telemetry-data">
                      <span className="fluent-telemetry-label">{t('telemetry_download', lang)}</span>
                      <strong className="fluent-telemetry-val"><TransferValue field="speedDown"/></strong>
                    </div>
                  </div>

                  <div className="fluent-telemetry-divider" />

                  {/* Upload */}
                  <div className="fluent-telemetry-item">
                    <div className="fluent-telemetry-icon-box fluent-telemetry-icon-box--up">
                      <ArrowUp size={14} />
                    </div>
                    <div className="fluent-telemetry-data">
                      <span className="fluent-telemetry-label">{t('telemetry_upload', lang)}</span>
                      <strong className="fluent-telemetry-val"><TransferValue field="speedUp"/></strong>
                    </div>
                  </div>

                  <div className="fluent-telemetry-divider" />

                  {/* Total Transferred */}
                  <div className="fluent-telemetry-item">
                    <div className="fluent-telemetry-icon-box fluent-telemetry-icon-box--total">
                      <ArrowDownUp size={14} />
                    </div>
                    <div className="fluent-telemetry-data">
                      <span className="fluent-telemetry-label">{lang === 'en' ? 'Transferred' : 'Трафик'}</span>
                      <strong className="fluent-telemetry-val"><TransferValue field="total"/></strong>
                    </div>
                  </div>

                  <div className="fluent-telemetry-divider" />

                  {/* Ping */}
                  <div className="fluent-telemetry-item">
                    <div className="fluent-telemetry-icon-box fluent-telemetry-icon-box--ping">
                      <Activity size={14} />
                    </div>
                    <div className="fluent-telemetry-data">
                      <span className="fluent-telemetry-label">{t('telemetry_ping', lang)}</span>
                      <strong className="fluent-telemetry-val">{formatServerPing(currentServer, lang)}</strong>
                    </div>
                  </div>
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="protection-summary"
                className="fluent-protection-summary"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 4 }}
                transition={springs.natural}
                layout
              >
                <div className="fluent-prot-pill">
                  <Lock size={12} />
                  <span>{t('prot_encryption', lang)}</span>
                </div>
                <div className="fluent-prot-pill">
                  <ShieldCheck size={12} />
                  <span>{isAndroid ? 'Sing-Box Ядро' : t('prot_wintun', lang)}</span>
                </div>
                <div className="fluent-prot-pill">
                  <Globe size={12} />
                  <span>{t('prot_nologs', lang)}</span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </div>
  );
}

export default React.memo(MainVpnScreen);
