import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Sliders,
  Monitor,
  Bell,
  Palette,
  Shield,
  FileText,
  Activity,
  Download,
  Check,
  RefreshCw,
  Info,
  X,
  Sparkles,
  ArrowUpCircle,
  Loader2,
} from 'lucide-react';

import { THEMES, t } from '../utils/i18n';
import { springs, buttonPress } from '../utils/motion';
import { isAndroid } from '../utils/platform';

function FluentSwitch({ checked, onChange, label }) {
  return (
    <motion.button
      type="button"
      className={`fluent-switch ${checked ? 'active' : ''}`}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={(e) => {
        e.stopPropagation();
        onChange();
      }}
      whileTap={{ scale: 0.94 }}
      transition={springs.tactile}
      layout
    >
      <motion.div
        className="fluent-switch-thumb"
        initial={false}
        animate={{ x: checked ? 19 : 0 }}
        transition={springs.tactile}
      />
    </motion.button>
  );
}

function SettingsScreen({
  settings,
  onUpdateSetting,
  clientVersion = '1.0.0',
  hwid,
  initialCategory = 'connection',
}) {
  const currentLang = settings?.language || 'ru';
  const [activeCategory, setActiveCategory] = useState(initialCategory); // 'connection' | 'windows' | 'notifications' | 'appearance' | 'advanced'
  useEffect(() => { setActiveCategory(initialCategory); }, [initialCategory]);
  const [logContent, setLogContent] = useState('');
  const [showLogModal, setShowLogModal] = useState(false);
  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const [installingUpdate, setInstallingUpdate] = useState(false);
  const [updateStatus, setUpdateStatus] = useState('idle'); // 'idle' | 'checking' | 'available' | 'up-to-date' | 'downloading' | 'ready' | 'error'
  const [updateInfo, setUpdateInfo] = useState(null);
  const [updateProgress, setUpdateProgress] = useState({ percent: 0, downloadedMb: '0', totalMb: '0.0', speedMb: '0.0' });
  const [updateError, setUpdateError] = useState('');

  useEffect(() => {
    if (window.electronAPI?.onUpdateProgress) {
      const unsub = window.electronAPI.onUpdateProgress((prog) => {
        if (prog) setUpdateProgress(prog);
      });
      return () => {
        if (typeof unsub === 'function') unsub();
      };
    }
  }, []);


  useEffect(() => {
    let mounted = true;
    const available = release => {
      if (!mounted || !release) return;
      setUpdateInfo(release);
      setUpdateStatus(previous => ['downloading', 'ready'].includes(previous) ? previous : 'available');
    };
    const unsubscribe = window.electronAPI?.onUpdateAvailable?.(available);
    window.electronAPI?.getUpdateStatus?.().then(status => {
      if (!mounted) return;
      if (status?.available) {
        available(status.available);
        if (status.downloadedVersion === status.available.version) setUpdateStatus('ready');
      }
    }).catch(() => {});
    return () => { mounted = false; if (typeof unsubscribe === 'function') unsubscribe(); };
  }, []);

  const categories = [
    { id: 'connection', label: t('cat_connection', currentLang), icon: Sliders },
    { id: 'windows', label: isAndroid ? 'Android' : t('cat_windows', currentLang), icon: Monitor },
    { id: 'notifications', label: t('cat_notifications', currentLang), icon: Bell },
    { id: 'appearance', label: t('cat_appearance', currentLang), icon: Palette },
    { id: 'advanced', label: t('cat_advanced', currentLang), icon: Shield },
  ];

  const handleExportLog = async () => {
    if (window.electronAPI?.exportDebugLog) {
      const text = await window.electronAPI.exportDebugLog();
      setLogContent(text);
      setShowLogModal(true);
    } else {
      setLogContent('Экспорт логов доступен только в настольной среде Windows.');
      setShowLogModal(true);
    }
  };

  const handleCheckUpdate = async () => {
    setCheckingUpdate(true); setUpdateStatus('checking'); setUpdateError('');
    try {
      if (!window.electronAPI?.checkForUpdates) throw new Error('Обновления доступны только в приложении Windows');
      const result = await window.electronAPI.checkForUpdates();
      if (!result?.ok) throw new Error(result?.error || 'Не удалось проверить обновления');
      setUpdateInfo(result.hasUpdate ? result.releaseInfo : null);
      setUpdateStatus(result.hasUpdate ? 'available' : 'up-to-date');
    } catch (error) {
      setUpdateStatus('error');
      setUpdateError(error.message || 'Не удалось связаться с сервером обновлений');
    } finally { setCheckingUpdate(false); }
  };
  const handleDownloadUpdate = async () => {
    if (!updateInfo) return;
    setUpdateStatus('downloading'); setUpdateError('');
    setUpdateProgress({ percent: 0, downloadedMb: '0.0', totalMb: (updateInfo.sizeBytes / 1048576).toFixed(1), speedMb: '0.0' });
    try {
      if (!window.electronAPI?.downloadUpdate) throw new Error('Загрузка обновлений недоступна');
      const result = await window.electronAPI.downloadUpdate(updateInfo);
      if (!result?.ok) throw new Error(result?.error || 'Ошибка загрузки обновления');
      setUpdateStatus('ready');
    } catch (error) { setUpdateStatus('error'); setUpdateError(error.message || 'Ошибка загрузки обновления'); }
  };
  const handleApplyUpdateAndRestart = async () => {
    if (installingUpdate) return;
    setInstallingUpdate(true); setUpdateError('');
    try {
      if (!window.electronAPI?.applyUpdateAndRestart) throw new Error('Установка обновлений недоступна');
      const result = await window.electronAPI.applyUpdateAndRestart(updateInfo);
      if (result?.needsPermission) { setUpdateError(result.error); return; }
      if (!result?.ok) throw new Error(result?.error || 'Установщик не запущен');
    } catch (error) { setUpdateStatus('error'); setUpdateError(error.message || 'Не удалось запустить установщик'); }
    finally { setInstallingUpdate(false); }
  };

  useEffect(() => { if (initialCategory === 'advanced') handleCheckUpdate(); }, [initialCategory]);

  return (
    <div className="fluent-page fluent-settings-page">
      <div className="fluent-settings-layout">
        {/* Left Subnav Tabs with Sliding Layout Spring */}
        <div className="fluent-settings-tabs">
          {categories.map((cat) => {
            const Icon = cat.icon;
            const isActive = activeCategory === cat.id;
            return (
              <motion.button
                key={cat.id}
                type="button"
                className={`fluent-settings-tab-btn ${isActive ? 'active' : ''}`}
                onClick={() => setActiveCategory(cat.id)}
                aria-pressed={isActive}
                whileHover={{ y: -2 }}
                whileTap={{ scale: 0.97 }}
                transition={springs.tactile}
              >
                {isActive && (
                  <motion.div
                    className="fluent-settings-tab-active-bg"
                    layoutId="settings-active-cat-bg"
                    transition={springs.pill}
                  />
                )}
                <Icon size={16} />
                <span>{cat.label}</span>
              </motion.button>
            );
          })}
        </div>

        {/* Right Settings Body with Silky Section Transitions */}
        <div className="fluent-settings-content">
          <AnimatePresence mode="wait">
            {/* 1. ПОДКЛЮЧЕНИЕ */}
            {activeCategory === 'connection' && (
              <motion.div
                key="cat-connection"
                className="fluent-settings-section"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={springs.natural}
              >
                <h3 className="fluent-section-title">{t('cat_connection', currentLang)}</h3>
                <p className="fluent-section-desc">
                  {currentLang === 'en' ? 'Manage connection rules and leak prevention.' : 'Управление правилами сетевого соединения и защитой от утечек.'}
                </p>

                <div className="fluent-card fluent-settings-group">
                  {/* Автоподключение */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('autoConnect', !settings.autoConnect)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_autoconnect', currentLang)}</strong>
                      <span>{t('setting_autoconnect_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={Boolean(settings.autoConnect)}
                      label={t('setting_autoconnect', currentLang)}
                      onChange={() => onUpdateSetting('autoConnect', !settings.autoConnect)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  {/* Автопереподключение */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('autoReconnect', settings.autoReconnect !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_autoreconnect', currentLang)}</strong>
                      <span>{t('setting_autoreconnect_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.autoReconnect !== false}
                      label={t('setting_autoreconnect', currentLang)}
                      onChange={() => onUpdateSetting('autoReconnect', settings.autoReconnect !== false ? false : true)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  <div className="fluent-toggle-row">
                    <div className="fluent-toggle-text">
                      <strong>{currentLang === 'en' ? 'System VPN controls' : 'Системное подключение VPN'}</strong>
                      <span>{currentLang === 'en' ? 'Manage VPN permissions in Android settings.' : 'Управляйте разрешениями VPN в настройках Android.'}</span>
                    </div>
                    <button className="btn btn--secondary btn--sm" onClick={() => window.electronAPI?.openVpnSettings?.()}>{currentLang === 'en' ? 'Open' : 'Открыть'}</button>
                  </div>
                  <div className="fluent-toggle-divider" />

                  {/* Разрешить локальную сеть */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('allowLan', settings.allowLan !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_allowlan', currentLang)}</strong>
                      <span>{t('setting_allowlan_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.allowLan !== false}
                      label={t('setting_allowlan', currentLang)}
                      onChange={() => onUpdateSetting('allowLan', settings.allowLan !== false ? false : true)}
                    />
                  </div>
                </div>
              </motion.div>
            )}

            {activeCategory === 'windows' && (
              <motion.div key="cat-android" className="fluent-settings-section" initial={{opacity:0,y:6}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-4}} transition={springs.natural}>
                <h3 className="fluent-section-title">Android</h3><div className="fluent-card fluent-settings-group"><div className="fluent-toggle-row"><div className="fluent-toggle-text"><strong>Viht VPN в шторке</strong><span>Плитка для быстрого подключения и отключения VPN.</span></div><button className="btn btn--secondary btn--sm" onClick={() => window.electronAPI?.addQuickTile?.()}>Добавить</button></div></div>
                <p className="fluent-section-desc">{currentLang === 'en' ? 'Connection permissions, background operation, and notifications.' : 'Разрешения, фоновая работа и системные уведомления.'}</p>
                <div className="fluent-card fluent-settings-group">
                  <div className="fluent-toggle-row"><div className="fluent-toggle-text"><strong>{currentLang === 'en' ? 'VPN permission' : 'Разрешение на VPN'}</strong><span>{currentLang === 'en' ? 'Confirm Android permission on the first connection.' : 'Android запрашивает разрешение при первом подключении.'}</span></div><button className="btn btn--secondary btn--sm" onClick={() => window.electronAPI?.openVpnSettings?.()}>{currentLang === 'en' ? 'Open' : 'Открыть'}</button></div>
                  <div className="fluent-toggle-divider" />
                  <div className="fluent-toggle-row"><div className="fluent-toggle-text"><strong>{currentLang === 'en' ? 'Notifications' : 'Уведомления'}</strong><span>{currentLang === 'en' ? 'Android displays an ongoing VPN notification while connected.' : 'Во время работы Android показывает постоянное уведомление VPN.'}</span></div><button className="btn btn--secondary btn--sm" onClick={() => window.electronAPI?.openNotificationSettings?.()}>{currentLang === 'en' ? 'Open' : 'Открыть'}</button></div>
                  <div className="fluent-toggle-divider" />
                  <div className="fluent-toggle-row"><div className="fluent-toggle-text"><strong>{currentLang === 'en' ? 'Background connection' : 'Фоновое подключение'}</strong><span>{currentLang === 'en' ? 'Closing this screen keeps the active VPN running. Use Disconnect to stop it.' : 'Закрытие экрана сохраняет активное подключение. Если Android ограничивает фон, разрешите приложению работу без ограничений в настройках батареи.'}</span></div><button className="btn btn--secondary btn--sm" onClick={() => window.electronAPI?.openBatterySettings?.()}>{currentLang === 'en' ? 'Settings' : 'Настройки'}</button></div>
                </div>
              </motion.div>
            )}

            {/* 3. УВЕДОМЛЕНИЯ */}
            {activeCategory === 'notifications' && (
              <motion.div
                key="cat-notifications"
                className="fluent-settings-section"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={springs.natural}
              >
                <h3 className="fluent-section-title">{t('cat_notifications', currentLang)}</h3>
                <p className="fluent-section-desc">
                  {currentLang === 'en' ? 'Alerts in Windows 10/11 Notification Center.' : 'Оповещения в Центре уведомлений Windows 10/11.'}
                </p>

                <div className="fluent-card fluent-settings-group">
                  {/* О подключении */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('notifyConnected', settings.notifyConnected !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_notify_conn', currentLang)}</strong>
                      <span>{t('setting_notify_conn_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.notifyConnected !== false}
                      label={t('setting_notify_conn', currentLang)}
                      onChange={() => onUpdateSetting('notifyConnected', settings.notifyConnected !== false ? false : true)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  {/* Об отключении */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('notifyDisconnected', settings.notifyDisconnected !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_notify_disconn', currentLang)}</strong>
                      <span>{t('setting_notify_disconn_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.notifyDisconnected !== false}
                      label={t('setting_notify_disconn', currentLang)}
                      onChange={() => onUpdateSetting('notifyDisconnected', settings.notifyDisconnected !== false ? false : true)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  {/* О потере соединения */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('notifyLost', settings.notifyLost !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_notify_lost', currentLang)}</strong>
                      <span>{t('setting_notify_lost_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.notifyLost !== false}
                      label={t('setting_notify_lost', currentLang)}
                      onChange={() => onUpdateSetting('notifyLost', settings.notifyLost !== false ? false : true)}
                    />
                  </div>
                  <div className="fluent-toggle-divider" />
                  <div className="fluent-toggle-row" onClick={() => onUpdateSetting('notifyUpdate', settings.notifyUpdate === false)}>
                    <div className="fluent-toggle-text">
                      <strong>{currentLang === 'en' ? 'Available updates' : 'Доступные обновления'}</strong>
                      <span>{currentLang === 'en' ? 'Notify when a new version is available.' : 'Сообщать о новых версиях приложения.'}</span>
                    </div>
                    <FluentSwitch checked={settings.notifyUpdate !== false} label={currentLang === 'en' ? 'Available updates' : 'Доступные обновления'} onChange={() => onUpdateSetting('notifyUpdate', settings.notifyUpdate === false)} />
                  </div>
                </div>
              </motion.div>
            )}

            {/* 4. ВНЕШНИЙ ВИД (THEMES & LANGUAGE) */}
            {activeCategory === 'appearance' && (
              <motion.div
                key="cat-appearance"
                className="fluent-settings-section"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={springs.natural}
              >
                <h3 className="fluent-section-title">{t('cat_appearance', currentLang)}</h3>
                <p className="fluent-section-desc">{t('setting_theme_desc', currentLang)}</p>

                {/* Language Selection Group */}
                <div className="fluent-card fluent-settings-group" style={{ marginBottom: 14 }}>
                  <div className="fluent-toggle-row">
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_lang_title', currentLang)}</strong>
                      <span>{t('setting_lang_desc', currentLang)}</span>
                    </div>
                    <div className="fluent-lang-toggle-group">
                      <motion.button
                        type="button"
                        className={`fluent-lang-btn ${currentLang === 'ru' ? 'active' : ''}`}
                        aria-pressed={currentLang === 'ru'}
                        onClick={() => onUpdateSetting('language', 'ru')}
                        whileTap={{ scale: 0.95 }}
                        transition={springs.tactile}
                      >
                        {currentLang === 'ru' && <motion.span className="liquid-segment-lens" layoutId="language-lens" transition={springs.pill} />}
                        <span>🇷🇺</span>
                        <span>Русский</span>
                      </motion.button>
                      <motion.button
                        type="button"
                        className={`fluent-lang-btn ${currentLang === 'en' ? 'active' : ''}`}
                        aria-pressed={currentLang === 'en'}
                        onClick={() => onUpdateSetting('language', 'en')}
                        whileTap={{ scale: 0.95 }}
                        transition={springs.tactile}
                      >
                        {currentLang === 'en' && <motion.span className="liquid-segment-lens" layoutId="language-lens" transition={springs.pill} />}
                        <span>🇬🇧</span>
                        <span>English</span>
                      </motion.button>
                    </div>
                  </div>
                </div>

                {/* Themes Selection Group */}
                <div className="fluent-card fluent-settings-group">
                  <div className="fluent-toggle-text" style={{ padding: '6px 4px 12px 4px' }}>
                    <strong>{t('setting_theme_title', currentLang)}</strong>
                    <span>{t('setting_theme_desc', currentLang)}</span>
                  </div>

                  <div className="fluent-theme-grid">
                    {THEMES.map((th) => {
                      const isSelected = (settings.theme || 'emerald') === th.id;
                      const themeName = currentLang === 'en' ? th.nameEn : th.nameRu;
                      return (
                        <motion.button
                          type="button"
                          aria-pressed={isSelected}
                          key={th.id}
                          className={`fluent-theme-card ${isSelected ? 'active' : ''}`}
                          onClick={() => onUpdateSetting('theme', th.id)}
                          title={themeName}
                          whileHover={{ y: -2, scale: 1.02 }}
                          whileTap={{ scale: 0.97 }}
                          transition={springs.tactile}
                        >
                          {isSelected && <motion.span className="liquid-theme-lens" layoutId="theme-lens" transition={springs.pill} />}
                          <div
                            className="fluent-theme-preview"
                            style={{
                              background: th.bg,
                              boxShadow: isSelected ? `0 0 16px ${th.accentGlow}` : undefined,
                              borderColor: isSelected ? th.primaryColor : 'rgba(255, 255, 255, 0.1)',
                            }}
                          >
                            <span
                              className="fluent-theme-dot"
                              style={{ background: th.primaryColor, color: th.primaryColor }}
                            />
                            <span
                              className="fluent-theme-dot"
                              style={{ background: th.secondaryColor, color: th.secondaryColor }}
                            />
                          </div>

                          <span className="fluent-theme-name">{themeName}</span>

                          {isSelected && (
                            <motion.div
                              className="fluent-theme-badge"
                              initial={{ scale: 0 }}
                              animate={{ scale: 1 }}
                              transition={springs.tactile}
                            >
                              <Check size={11} strokeWidth={3} />
                            </motion.div>
                          )}
                        </motion.button>
                      );
                    })}
                  </div>
                </div>
              </motion.div>
            )}

            {/* 5. РАСШИРЕННЫЕ */}
            {activeCategory === 'advanced' && (
              <motion.div
                key="cat-advanced"
                className="fluent-settings-section"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={springs.natural}
              >
                <h3 className="fluent-section-title">{t('cat_advanced', currentLang)}</h3>
                <p className="fluent-section-desc">
                  {currentLang === 'en' ? 'Diagnostics, DNS routing, and system logs.' : 'Диагностика, маршрутизация DNS и системные журналы.'}
                </p>

                <div className="fluent-card fluent-settings-group">
                  {/* DNS */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('dnsAdblock', settings.dnsAdblock !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_dns', currentLang)}</strong>
                      <span>{t('setting_dns_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.dnsAdblock !== false}
                      label={t('setting_dns', currentLang)}
                      onChange={() => onUpdateSetting('dnsAdblock', settings.dnsAdblock !== false ? false : true)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  {/* Split tunneling: Обход сайтов РФ */}
                  <div
                    className="fluent-toggle-row"
                    onClick={() => onUpdateSetting('bypassRussia', settings.bypassRussia !== false ? false : true)}
                  >
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_split_ru', currentLang)}</strong>
                      <span>{t('setting_split_ru_desc', currentLang)}</span>
                    </div>
                    <FluentSwitch
                      checked={settings.bypassRussia !== false}
                      label={t('setting_split_ru', currentLang)}
                      onChange={() => onUpdateSetting('bypassRussia', settings.bypassRussia !== false ? false : true)}
                    />
                  </div>

                  <div className="fluent-toggle-divider" />

                  {/* Экспорт логов */}
                  <div className="fluent-toggle-row">
                    <div className="fluent-toggle-text">
                      <strong>{t('setting_export_logs', currentLang)}</strong>
                      <span>{t('setting_export_logs_desc', currentLang)}</span>
                    </div>
                    <motion.button
                      type="button"
                      className="btn btn--secondary btn--sm"
                      onClick={handleExportLog}
                      {...buttonPress}
                    >
                      <FileText size={13} />
                      <span>{t('btn_export_logs', currentLang)}</span>
                    </motion.button>
                  </div>
                </div>

                {/* О программе & проверка обновлений */}
                <div className="fluent-card fluent-about-card">
                  <div className="fluent-about-header">
                    <Info size={16} />
                    <strong>{currentLang === 'en' ? 'About VihtVPN' : 'О приложении VihtVPN'}</strong>
                  </div>

                  <div className="fluent-about-rows">
                    <div className="fluent-about-row">
                      <span>{currentLang === 'en' ? 'Client version:' : 'Версия клиента:'}</span>
                      <strong>v{clientVersion} (Android 8+)</strong>
                    </div>
                    <div className="fluent-about-row">
                      <span>{currentLang === 'en' ? 'Network driver:' : 'Сетевой драйвер:'}</span>
                      <strong>Android VpnService / sing-box</strong>
                    </div>
                    <div className="fluent-about-row">
                      <span>{currentLang === 'en' ? 'Hardware HWID:' : 'Аппаратный HWID:'}</span>
                      <code>{hwid ? `${hwid.slice(0, 10)}...${hwid.slice(-6)}` : '—'}</code>
                    </div>
                  </div>

                  <div className="fluent-about-update-action">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <motion.button
                        type="button"
                        className="btn btn--secondary btn--sm"
                        onClick={handleCheckUpdate}
                        disabled={checkingUpdate || updateStatus === 'downloading'}
                        {...buttonPress}
                      >
                        <RefreshCw size={13} className={checkingUpdate ? 'spinning' : ''} />
                        <span>{checkingUpdate ? (currentLang === 'en' ? 'Checking...' : 'Проверка...') : (currentLang === 'en' ? 'Check for updates' : 'Проверить обновления')}</span>
                      </motion.button>

                      {updateStatus === 'up-to-date' && (
                        <span className="settings-badge-update-ready" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <Check size={11} /> {currentLang === 'en' ? 'Latest version installed' : 'У вас актуальная версия'}
                        </span>
                      )}

                      {updateStatus === 'available' && (
                        <span className="settings-badge-update-new" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <Sparkles size={11} /> {currentLang === 'en' ? `v${updateInfo?.version} available` : `Доступна v${updateInfo?.version}`}
                        </span>
                      )}
                    </div>

                    {/* Available Update Card */}
                    {updateStatus === 'available' && updateInfo && (
                      <motion.div
                        className="settings-update-card"
                        style={{ marginTop: '10px' }}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={springs.natural}
                      >
                        <div className="settings-update-top">
                          <div className="settings-update-meta">
                            <div className="settings-update-title-row">
                              <span className="settings-update-title">
                                {updateInfo.title || `Новая версия VihtVPN v${updateInfo.version}`}
                              </span>
                              <span className="settings-badge-update-new">НОВОЕ</span>
                            </div>
                            <span className="settings-update-desc">
                              {currentLang === 'en' ? `Release from ${updateInfo.releaseDate || '05.10.2026'}` : `Релиз от ${updateInfo.releaseDate || '05.10.2026'}`}
                            </span>
                          </div>
                        </div>

                        {updateInfo.changelog && Array.isArray(updateInfo.changelog) && (
                          <div className="settings-update-changelog-box">
                            <span className="settings-changelog-title">{currentLang === 'en' ? "What's new:" : 'Что нового:'}</span>
                            <ul className="settings-changelog-list">
                              {updateInfo.changelog.map((c, i) => (
                                <li key={i} className="settings-changelog-item">
                                  <span className="settings-changelog-icon">•</span>
                                  <span>{c}</span>
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}

                        <motion.button
                          type="button"
                          className="btn btn--primary btn--glow settings-update-action-btn"
                          onClick={handleDownloadUpdate}
                          {...buttonPress}
                        >
                          <Download size={14} />
                          <span>{currentLang === 'en' ? `Download Update (${updateInfo.sizeFormatted || (updateInfo.sizeBytes / 1048576).toFixed(1) + ' MB'})` : `Скачать обновление (${updateInfo.sizeFormatted || (updateInfo.sizeBytes / 1048576).toFixed(1) + ' МБ'})`}</span>
                        </motion.button>
                      </motion.div>
                    )}

                    {/* Downloading progress */}
                    {updateStatus === 'downloading' && (
                      <div className="settings-update-card" style={{ marginTop: '10px' }}>
                        <div className="settings-update-progress-box">
                          <div className="settings-update-progress-meta">
                            <span>{currentLang === 'en' ? `Downloading v${updateInfo?.version}...` : `Загрузка обновления v${updateInfo?.version}...`}</span>
                            <span style={{ color: 'var(--neon-green)', fontWeight: 700 }}>{updateProgress.percent}%</span>
                          </div>
                          <div className="settings-update-progress-track">
                            <div
                              className="settings-update-progress-bar"
                              style={{ width: `${updateProgress.percent}%` }}
                            />
                          </div>
                          <div className="settings-update-progress-meta">
                            <span>{updateProgress.downloadedMb} МБ / {updateProgress.totalMb} МБ</span>
                            <span>{updateProgress.speedMb} МБ/с</span>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Ready to apply */}
                    {updateStatus === 'ready' && (
                      <div className="settings-update-ready-box" style={{ marginTop: '10px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <Check size={16} style={{ color: 'var(--neon-green)' }} />
                          <strong style={{ color: '#fff', fontSize: '13px' }}>
                            {currentLang === 'en' ? 'Update ready to install!' : 'Обновление скачано и готово!'}
                          </strong>
                        </div>
                        <p className="settings-update-ready-text">
                          {currentLang === 'en'
                            ? `Version v${updateInfo?.version} is downloaded. Click the button to restart and apply.`
                            : `Файл v${updateInfo?.version} скачан. Нажмите кнопку для перезапуска и обновления.`}
                        </p>
                        <motion.button
                          type="button"
                          className="btn btn--primary btn--glow settings-update-apply-btn"
                          disabled={installingUpdate}
                          onClick={handleApplyUpdateAndRestart}
                          {...buttonPress}
                        >
                          <Sparkles size={14} />
                          <span>{currentLang === 'en' ? `Restart & Apply (v${updateInfo?.version})` : `Перезапустить и применить (v${updateInfo?.version})`}</span>
                        </motion.button>
                      </div>
                    )}

                    {updateError && (
                      <p className="settings-update-error" style={{ marginTop: '8px' }}>{updateError}</p>
                    )}
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Log Viewer Modal with Spring Backdrop and Card */}
      <AnimatePresence>
        {showLogModal && (
          <motion.div
            className="fluent-modal-overlay"
            onClick={() => setShowLogModal(false)}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <motion.div
              className="fluent-modal-card fluent-modal-card--log"
              onClick={(e) => e.stopPropagation()}
              initial={{ scale: 0.94, opacity: 0, y: 12 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.94, opacity: 0, y: 8 }}
              transition={springs.cushioned}
            >
              <div className="fluent-modal-header">
                <h3 className="fluent-modal-title">Системный журнал (electron-debug.log)</h3>
                <motion.button
                  type="button"
                  className="fluent-modal-close"
                  onClick={() => setShowLogModal(false)}
                  whileTap={{ scale: 0.9 }}
                >
                  <X size={16} />
                </motion.button>
              </div>
              <div className="fluent-modal-body">
                <pre className="fluent-log-pre">{logContent}</pre>
              </div>
              <div className="fluent-modal-footer">
                <motion.button
                  type="button"
                  className="btn btn--secondary btn--sm"
                  onClick={() => {
                    navigator.clipboard.writeText(logContent);
                    alert('Лог скопирован в буфер обмена!');
                  }}
                  {...buttonPress}
                >
                  Копировать в буфер
                </motion.button>
                <motion.button
                  type="button"
                  className="btn btn--primary btn--sm"
                  onClick={() => setShowLogModal(false)}
                  {...buttonPress}
                >
                  Закрыть
                </motion.button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default React.memo(SettingsScreen);

