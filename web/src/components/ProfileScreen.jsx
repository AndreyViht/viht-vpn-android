import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  User,
  CreditCard,
  ExternalLink,
  Shield,
  RotateCcw,
  LogOut,
  Check,
  RefreshCw,
  Calendar,
  Clock,
  Send,
  Loader2,
  Copy,
  AlertTriangle,
} from 'lucide-react';
import SocialIcon from './SocialIcon';
import { fetchPayments, resetSubscriptionKeys } from '../services/api';
import { fixMojibake, t } from '../utils/i18n';
import { springs, buttonPress, cardPress } from '../utils/motion';

function ProfileScreen({
  user,
  hwid,
  token,
  onLogout,
  onSwitchProvider,
  lang = 'ru',
}) {
  const [activeTab, setActiveTab] = useState('accounts'); // 'accounts' | 'payments'

  // Payments data
  const [payments, setPayments] = useState([]);
  const [paymentsLoading, setPaymentsLoading] = useState(false);
  const [paymentsLoaded, setPaymentsLoaded] = useState(false);

  // Key reset state
  const [resettingKeys, setResettingKeys] = useState(false);
  const [toast, setToast] = useState(null);

  const showToast = (message, isError = false) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  };

  const loadPayments = useCallback(async () => {
    if (!token) return;
    setPaymentsLoading(true);
    try {
      const res = await fetchPayments(token);
      if (res && res.ok) {
        setPayments(res.payments || []);
        setPaymentsLoaded(true);
      } else {
        showToast(res?.error || 'Не удалось загрузить историю платежей', true);
      }
    } catch (e) {
      showToast('Ошибка сети при загрузке платежей', true);
    } finally {
      setPaymentsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (activeTab === 'payments' && !paymentsLoaded) {
      loadPayments();
    }
  }, [activeTab, paymentsLoaded, loadPayments]);

  const handleResetKeys = async () => {
    if (!window.confirm('Вы уверены, что хотите сбросить ключи подключения? Все текущие ссылки на устройствах перестанут работать и потребуется обновить подключение.')) {
      return;
    }
    setResettingKeys(true);
    try {
      const res = await resetSubscriptionKeys(token);
      if (res && res.ok) {
        showToast('Ключи подписки успешно обновлены!');
      } else {
        showToast(res?.error || 'Не удалось сбросить ключи', true);
      }
    } catch (e) {
      showToast('Ошибка сети при сбросе ключей', true);
    } finally {
      setResettingKeys(false);
    }
  };

  const openCabinet = () => {
    const url = 'https://anviht.ru/lk';
    if (window.electronAPI?.openExternal) window.electronAPI.openExternal(url);
    else window.open(url, '_blank');
  };

  const formatSource = (src) => {
    switch (src) {
      case 'site': return 'ЮMoney / Сайт';
      case 'bot_grant': return 'Telegram Stars';
      case 'admin': return 'Администратор';
      case 'referral': return 'Бонус за друга';
      default: return src || 'Оплата онлайн';
    }
  };

  const formatSubTime = (u) => {
    if (!u) return '—';
    if (u.subscription_expires_at) {
      const diff = new Date(u.subscription_expires_at).getTime() - Date.now();
      if (diff > 0) {
        const totalHours = Math.floor(diff / (1000 * 60 * 60));
        const d = Math.floor(totalHours / 24);
        const h = totalHours % 24;
        return `${d} дн. ${h} ч.`;
      }
    }
    const d = u.subscription_days_left ?? 0;
    const h = u.subscription_hours_left ?? 0;
    return `${d} дн. ${h} ч.`;
  };

  const hasTelegram = Boolean(user?.telegram_id || (user?.authorizations || []).some(a => a.provider === 'telegram'));
  const hasVk = Boolean(user?.vk_ids?.length || (user?.authorizations || []).some(a => a.provider === 'vk'));
  const hasYandex = Boolean(user?.yandex_email || (user?.authorizations || []).some(a => a.provider === 'yandex'));

  const currentProvider = user?.auth_provider || user?.chosen_provider || (hasVk ? 'vk' : hasYandex ? 'yandex' : 'tg');

  return (
    <div className="fluent-page fluent-profile-page">
      {/* Toast Alert */}
      <AnimatePresence>
        {toast && (
          <motion.div
            className={`fluent-toast ${toast.isError ? 'fluent-toast--error' : 'fluent-toast--success'}`}
            initial={{ opacity: 0, y: -12, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.95 }}
            transition={springs.cushioned}
          >
            <span>{toast.message}</span>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Page Header */}
      <div className="fluent-page-header">
        <button type="button" className="btn btn--primary btn--sm" onClick={() => window.dispatchEvent(new Event('viht-open-tariffs'))}>Продлить подписку</button>
        <motion.button data-motion-press="true"
          type="button"
          className="btn btn--secondary btn--sm"
          onClick={openCabinet}
          title={t('cabinet_btn', lang)}
          {...buttonPress}
        >
          <span>{t('cabinet_btn', lang)}</span>
          <ExternalLink size={13} />
        </motion.button>
      </div>

      {/* Tabs Switcher with Sliding Active Pill */}
      <div className="fluent-profile-tabs">
        <button
          type="button"
          className={`fluent-profile-tab ${activeTab === 'accounts' ? 'active' : ''}`}
          onClick={() => setActiveTab('accounts')}
          aria-pressed={activeTab === 'accounts'}
        >
          {activeTab === 'accounts' && (
            <motion.div
              className="fluent-profile-tab-active-bg"
              layoutId="profile-tab-pill"
              transition={springs.pill}
            />
          )}
          <User size={15} />
          <span>{t('tab_accounts', lang)}</span>
        </button>

        <button
          type="button"
          className={`fluent-profile-tab ${activeTab === 'payments' ? 'active' : ''}`}
          onClick={() => setActiveTab('payments')}
          aria-pressed={activeTab === 'payments'}
        >
          {activeTab === 'payments' && (
            <motion.div
              className="fluent-profile-tab-active-bg"
              layoutId="profile-tab-pill"
              transition={springs.pill}
            />
          )}
          <CreditCard size={15} />
          <span>{t('tab_payments', lang)}</span>
        </button>
      </div>

      <AnimatePresence mode="wait">
        {/* ─── TAB 1: АККАУНТЫ ─── */}
        {activeTab === 'accounts' && (
          <motion.div
            key="tab-accounts"
            className="fluent-profile-content"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={springs.natural}
          >
            {/* Main User Card */}
            <motion.div className="fluent-card fluent-user-hero-card" {...cardPress}>
              {(() => {
                const rawName = user?.vk_name || user?.yandex_name || (user?.telegram_username ? `@${user.telegram_username}` : 'Пользователь VihtVPN');
                const cleanDisplayName = fixMojibake(rawName);
                const avatarChar = (cleanDisplayName || 'U').charAt(0).toUpperCase();

                return (
                  <>
                    <div className="fluent-user-avatar-box">
                      <span className="fluent-user-avatar-initial">{avatarChar}</span>
                      <SocialIcon type={currentProvider} size={16} className="fluent-user-avatar-badge" />
                    </div>

                    <div className="fluent-user-hero-info">
                      <div className="fluent-user-name-line">
                        <strong className="fluent-user-hero-name">{cleanDisplayName}</strong>
                        <span className={`fluent-provider-tag fluent-provider-tag--${currentProvider}`}>
                          {currentProvider === 'vk' ? 'VK ID' : currentProvider === 'yandex' ? 'Яндекс ID' : 'Telegram'}
                        </span>
                      </div>

                      <div className="fluent-user-hero-meta">
                        <span>{t('user_sub_label', lang)} <strong>{t('user_status_active', lang)} • {formatSubTime(user)}</strong></span>
                        {user?.yandex_email && (
                          <>
                            <span className="fluent-meta-bullet">•</span>
                            <span>{user.yandex_email}</span>
                          </>
                        )}
                      </div>
                    </div>
                  </>
                );
              })()}
            </motion.div>

            {/* Linked Providers Section */}
            <div className="fluent-card fluent-auth-methods-card">
              <div className="fluent-auth-methods-header">
                <div>
                  <h3 className="fluent-section-title">{t('linked_auth_title', lang)}</h3>
                  <p className="fluent-section-desc">
                    {t('linked_auth_desc', lang)} ({Array.isArray(user?.authorizations) ? user.authorizations.length : 0} {t('accounts_word', lang)}).
                  </p>
                </div>
                <motion.button data-motion-press="true"
                  type="button"
                  className="btn btn--secondary btn--xs"
                  onClick={openCabinet}
                  title="Управление привязками в личном кабинете на сайте"
                  {...buttonPress}
                >
                  <span>{t('btn_manage', lang)}</span>
                  <ExternalLink size={12} />
                </motion.button>
              </div>

              {Array.isArray(user?.authorizations) && user.authorizations.length > 0 ? (
                <div className="fluent-auth-grid">
                  {user.authorizations.map((auth, idx) => {
                    const cleanName = fixMojibake(auth.name || '');
                    const cleanId = fixMojibake(auth.identifier || '');
                    const isCur = Boolean(
                      auth.is_current ||
                      (currentProvider === 'telegram' && auth.provider === 'telegram' && (String(auth.identifier) === String(user?.telegram_id) || auth.is_primary)) ||
                      (currentProvider === 'vk' && auth.provider === 'vk') ||
                      (currentProvider === 'yandex' && auth.provider === 'yandex' && (
                        auth.email === user?.yandex_email ||
                        auth.identifier === user?.yandex_email ||
                        (user?.yandex_name && cleanName === fixMojibake(user.yandex_name))
                      ))
                    );
                    const providerType = auth.provider === 'telegram' ? 'tg' : auth.provider;
                    const displayValue = cleanName || cleanId || (auth.email ? auth.email : 'Активен');
                    const subValue = (auth.email && cleanName && auth.email !== cleanName)
                      ? auth.email
                      : (cleanId && cleanId !== cleanName ? cleanId : null);

                    return (
                      <motion.div
                        key={idx}
                        className={`fluent-auth-tile linked ${isCur ? 'fluent-auth-tile--current' : ''}`}
                        whileHover={{ y: -2, scale: 1.01 }}
                        transition={springs.tactile}
                      >
                        <div className={`fluent-auth-tile__icon-wrap ${providerType}`}>
                          <SocialIcon type={providerType} size={15} />
                        </div>
                        <div className="fluent-auth-tile__info">
                          <div className="fluent-auth-tile__header">
                            <strong className="fluent-auth-tile__title">
                              {auth.title || (auth.provider === 'telegram' ? 'Telegram' : auth.provider === 'vk' ? 'ВКонтакте' : 'Яндекс ID')}
                            </strong>
                            <span className={`fluent-chip ${isCur ? 'fluent-chip--ok' : 'fluent-chip--mute'}`}>
                              {isCur ? t('chip_current_login', lang) : t('chip_linked', lang)}
                            </span>
                          </div>
                          <div className="fluent-auth-tile__value" title={displayValue}>
                            {displayValue}
                          </div>
                          {subValue && (
                            <div className="fluent-auth-tile__sub" title={subValue}>{subValue}</div>
                          )}
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              ) : (
                <div className="fluent-auth-grid">
                  <div className={`fluent-auth-tile ${hasTelegram ? 'linked' : 'unlinked'}`}>
                    <div className="fluent-auth-tile__icon-wrap tg">
                      <SocialIcon type="tg" size={20} />
                    </div>
                    <div className="fluent-auth-tile__info">
                      <div className="fluent-auth-tile__header">
                        <strong>Telegram</strong>
                        <span className={`fluent-chip ${hasTelegram ? 'fluent-chip--ok' : 'fluent-chip--mute'}`}>
                          {hasTelegram ? 'Привязан' : 'Не привязан'}
                        </span>
                      </div>
                      <span className="fluent-auth-tile__value">
                        {user?.telegram_username ? `@${user.telegram_username}` : (user?.telegram_id ? `ID: ${user.telegram_id}` : 'Не привязан')}
                      </span>
                    </div>
                  </div>

                  <div className={`fluent-auth-tile ${hasVk ? 'linked' : 'unlinked'}`}>
                    <div className="fluent-auth-tile__icon-wrap vk">
                      <SocialIcon type="vk" size={20} />
                    </div>
                    <div className="fluent-auth-tile__info">
                      <div className="fluent-auth-tile__header">
                        <strong>VK ID</strong>
                        <span className={`fluent-chip ${hasVk ? 'fluent-chip--ok' : 'fluent-chip--mute'}`}>
                          {hasVk ? 'Привязан' : 'Не привязан'}
                        </span>
                      </div>
                      <span className="fluent-auth-tile__value">
                        {user?.vk_name || (user?.vk_ids?.[0] ? `id${user.vk_ids[0]}` : 'Не привязан')}
                      </span>
                    </div>
                  </div>

                  <div className={`fluent-auth-tile ${hasYandex ? 'linked' : 'unlinked'}`}>
                    <div className="fluent-auth-tile__icon-wrap yandex">
                      <SocialIcon type="yandex" size={20} />
                    </div>
                    <div className="fluent-auth-tile__info">
                      <div className="fluent-auth-tile__header">
                        <strong>Яндекс ID</strong>
                        <span className={`fluent-chip ${hasYandex ? 'fluent-chip--ok' : 'fluent-chip--mute'}`}>
                          {hasYandex ? 'Привязан' : 'Не привязан'}
                        </span>
                      </div>
                      <span className="fluent-auth-tile__value">
                        {user?.yandex_email || 'Не привязан'}
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {/* Quick Switcher If Multiple Linked */}
              {Array.isArray(user?.authorizations) && user.authorizations.length > 1 && (
                <div className="fluent-switcher-box">
                  <span className="fluent-switcher-title">{t('switch_auth_title', lang)}</span>
                  <div className="fluent-switcher-chips">
                    {user.authorizations.map((auth, idx) => {
                      const isCur = auth.provider === currentProvider;
                      const cleanName = fixMojibake(auth.name || auth.identifier);
                      return (
                        <motion.button data-motion-press="true"
                          key={idx}
                          type="button"
                          className={`fluent-switcher-chip ${isCur ? 'active' : ''}`}
                          onClick={() => onSwitchProvider && onSwitchProvider(auth.provider)}
                          aria-pressed={isCur}
                          whileHover={{ scale: 1.03 }}
                          whileTap={{ scale: 0.95 }}
                          transition={springs.tactile}
                        >
                          {isCur && <motion.span className="liquid-segment-lens" layoutId="account-switch-lens" transition={springs.pill} />}
                          <SocialIcon type={auth.provider === 'telegram' ? 'tg' : auth.provider} size={14} />
                          <span>{cleanName}</span>
                          {isCur && <Check size={12} />}
                        </motion.button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>

            {/* Account Security & Actions Card */}
            <div className="fluent-card fluent-account-actions-card">
              <h3 className="fluent-section-title">{t('sec_title', lang)}</h3>

              <div className="fluent-action-rows">
                <div className="fluent-action-row">
                  <div className="fluent-action-row__text">
                    <strong className="fluent-action-title">{t('sec_reset_keys_title', lang)}</strong>
                    <span className="fluent-action-desc">
                      {t('sec_reset_keys_desc', lang)}
                    </span>
                  </div>
                  <motion.button data-motion-press="true"
                    type="button"
                    className="btn btn--secondary btn--sm"
                    onClick={handleResetKeys}
                    disabled={resettingKeys}
                    {...buttonPress}
                  >
                    <RotateCcw size={13} className={resettingKeys ? 'spinning' : ''} />
                    <span>{resettingKeys ? t('btn_resetting', lang) : t('btn_reset_keys', lang)}</span>
                  </motion.button>
                </div>

                <div className="fluent-action-divider" />

                <div className="fluent-action-row">
                  <div className="fluent-action-row__text">
                    <strong className="fluent-action-title">{t('sec_logout_title', lang)}</strong>
                    <span className="fluent-action-desc">
                      {t('sec_logout_desc', lang)}
                    </span>
                  </div>
                  <motion.button data-motion-press="true"
                    type="button"
                    className="btn btn--secondary btn--sm btn--danger"
                    onClick={onLogout}
                    {...buttonPress}
                  >
                    <LogOut size={13} />
                    <span>{t('sec_logout_btn', lang)}</span>
                  </motion.button>
                </div>
              </div>
            </div>
          </motion.div>
        )}

        {/* ─── TAB 2: ИСТОРИЯ ПЛАТЕЖЕЙ ─── */}
        {activeTab === 'payments' && (
          <motion.div
            key="tab-payments"
            className="fluent-profile-content"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={springs.natural}
          >
            {/* Summary Stat Cards */}
            <div className="fluent-payments-summary-grid">
              <motion.div className="fluent-card fluent-stat-tile" {...cardPress}>
                <span className="fluent-stat-label">{t('tx_total', lang)}</span>
                <strong className="fluent-stat-num">{payments.length}</strong>
                <span className="fluent-stat-desc">{t('tx_success_label', lang)}</span>
              </motion.div>

              <motion.div className="fluent-card fluent-stat-tile" {...cardPress}>
                <span className="fluent-stat-label">{t('tx_days_granted', lang)}</span>
                <strong className="fluent-stat-num fluent-stat-num--green">
                  +{payments.reduce((acc, p) => acc + (Number(p.days_added) || 0), 0)} {t('days_short', lang)}
                </strong>
                <span className="fluent-stat-desc">{t('tx_days_desc', lang)}</span>
              </motion.div>

              <motion.div className="fluent-card fluent-stat-tile" {...cardPress}>
                <span className="fluent-stat-label">{t('tx_total_paid', lang)}</span>
                <strong className="fluent-stat-num fluent-stat-num--blue">
                  {payments.reduce((acc, p) => acc + (Number(p.amount_rub) || 0), 0)} ₽
                </strong>
                <span className="fluent-stat-desc">{t('tx_paid_desc', lang)}</span>
              </motion.div>
            </div>

            {/* Transactions List */}
            <div className="fluent-card fluent-payments-list-card">
              <div className="fluent-payments-header-row">
                <h3 className="fluent-section-title">{t('tx_history_title', lang)}</h3>
                <motion.button data-motion-press="true"
                  type="button"
                  className="btn btn--secondary btn--sm"
                  onClick={loadPayments}
                  disabled={paymentsLoading}
                  {...buttonPress}
                >
                  <RefreshCw size={13} className={paymentsLoading ? 'spinning' : ''} />
                  <span>{paymentsLoading ? t('devices_refreshing', lang) : t('devices_refresh', lang)}</span>
                </motion.button>
              </div>

              {paymentsLoading ? (
                <div className="fluent-loading-box">
                  <Loader2 size={24} className="spinning fluent-spinner-glow" />
                  <span>{t('tx_loading', lang)}</span>
                </div>
              ) : payments.length === 0 ? (
                <div className="fluent-empty-box">
                  <CreditCard size={36} className="fluent-empty-icon" />
                  <h3>{t('tx_empty_title', lang)}</h3>
                  <p>{t('tx_empty_desc', lang)}</p>
                </div>
              ) : (
                <div className="fluent-tx-list">
                  {payments.map((p, idx) => {
                    const pDate = new Date(p.created_at || Date.now());
                    const days = Number(p.days_added) || 0;
                    const amount = Number(p.amount_rub) || 0;

                    return (
                      <motion.div
                        key={idx}
                        className="fluent-tx-row"
                        whileHover={{ y: -1, scale: 1.003 }}
                        transition={springs.tactile}
                      >
                        <div className="fluent-tx-icon-wrap">
                          <CreditCard size={16} />
                        </div>

                        <div className="fluent-tx-main">
                          <div className="fluent-tx-title-row">
                            <strong className="fluent-tx-service">Viht VPN</strong>
                            <span className="fluent-meta-bullet">•</span>
                            <span className="fluent-tx-source">{formatSource(p.source)}</span>
                            <span className="fluent-chip fluent-chip--ok">{t('tx_status_success', lang)}</span>
                          </div>
                          <div className="fluent-tx-date">
                            <Calendar size={12} />
                            <span>
                              {pDate.toLocaleDateString(lang === 'en' ? 'en-US' : 'ru-RU', {
                                day: '2-digit',
                                month: '2-digit',
                                year: 'numeric',
                              })} {lang === 'en' ? 'at' : 'в'} {pDate.toLocaleTimeString(lang === 'en' ? 'en-US' : 'ru-RU', { hour: '2-digit', minute: '2-digit' })}
                            </span>
                          </div>
                        </div>

                        <div className="fluent-tx-side">
                          <span className="fluent-tx-days">+{days} {t('days_added_suffix', lang)}</span>
                          <strong className="fluent-tx-amount">{amount} ₽</strong>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default React.memo(ProfileScreen);

